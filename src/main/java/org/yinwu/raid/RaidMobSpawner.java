package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.util.MythicMobsIntegration;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class RaidMobSpawner {
    private final YinwuRaidPlugin plugin;
    private final RaidMobManager mobManager;
    private final SpecialRaidListener listener;
    private final ConfigManager configManager;
    private final MythicMobsIntegration mythicMobs;

    public RaidMobSpawner(YinwuRaidPlugin plugin, RaidMobManager mobManager, SpecialRaidListener listener, MythicMobsIntegration mythicMobs) {
        this.plugin = plugin; this.mobManager = mobManager; this.listener = listener;
        this.configManager = plugin.getConfigManager(); this.mythicMobs = mythicMobs;
    }

    public LivingEntity spawnRaidMob(Location center, int doomLevel, int wave, int totalWaves, int villageRadius,
                                     java.util.List<String> mobTypes) {
        Location loc = findValidSpawnLocation(center, villageRadius);
        if (loc == null) return null;

        // 精英判定：掷精英概率，命中则从精英表选怪（elite-mobs 配置了才生效）
        boolean elite = false;
        List<String> elitePool = mobManager.getEliteMobs().get(doomLevel);
        if (elitePool != null && !elitePool.isEmpty()) {
            double chance = mobManager.getEliteChances().getOrDefault(doomLevel, 0.15);
            elite = ThreadLocalRandom.current().nextDouble() < chance;
        }
        List<String> pool = elite ? elitePool : mobTypes;
        if (pool == null || pool.isEmpty()) {
            // 配置缺失时回退到写死的兜底类型
            pool = java.util.List.of(selectMobType(doomLevel, wave).name());
        }

        String entry = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        World w = loc.getWorld();

        LivingEntity entity = null;
        if (entry.startsWith("mythicmob:")) {
            String id = entry.substring("mythicmob:".length()).trim();
            if (!id.isEmpty() && mythicMobs != null) {
                entity = mythicMobs.spawnMythicMob(id, loc);
            }
            if (entity == null) {
                entity = spawnVanilla(loc, selectMobType(doomLevel, wave));
            }
        } else {
            entity = spawnVanilla(loc, parseType(entry, doomLevel, wave));
        }

        if (entity != null) {
            entity.setPersistent(true); // 防止远离玩家时被原版卸载/消失，保持计数准确
            mobManager.trackMob(entity);
            applyWaveScaling(entity, doomLevel, wave, totalWaves);
            if (elite) {
                double hpMult = mobManager.getEliteHealthMultiplier();
                entity.setMaxHealth(entity.getMaxHealth() * hpMult);
                entity.setHealth(entity.getMaxHealth());
                // 精英伤害加成（通过攻击力属性生效）
                var dmgAttr = entity.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                if (dmgAttr != null) {
                    dmgAttr.setBaseValue(dmgAttr.getBaseValue() * mobManager.getEliteDamageMultiplier());
                }
            }
        }
        return entity;
    }

    /** 生成原版实体（类型解析失败或生成异常时返回 null） */
    private LivingEntity spawnVanilla(Location loc, EntityType type) {
        try {
            return (LivingEntity) loc.getWorld().spawnEntity(loc, type);
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析怪物表条目为 EntityType，非法条目回退写死兜底 */
    private EntityType parseType(String entry, int doomLevel, int wave) {
        try {
            return EntityType.valueOf(entry.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return selectMobType(doomLevel, wave);
        }
    }

    /**
     * 在村庄外圈寻找生成位置（与原版一致：怪先聚村外，再攻入村庄）。
     * villageRadius 为村庄半径。
     */
    public Location findValidSpawnLocation(Location center, int villageRadius) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cz = center.getBlockZ();
        // 村庄外合理距离（避免太远导致区块卸载、计数归零）
        int ringMin = Math.max(24, villageRadius + 4);
        int ringMax = villageRadius + 24;
        for (int i = 0; i < 40; i++) {
            int x = cx + ThreadLocalRandom.current().nextInt(-ringMax, ringMax + 1);
            int z = cz + ThreadLocalRandom.current().nextInt(-ringMax, ringMax + 1);
            int dx = x - cx, dz = z - cz;
            if (dx * dx + dz * dz < ringMin * ringMin) continue; // 拒绝村庄内
            // Folia：候选点不在当前区域则跳过（避免 getHighestBlockYAt 跨区域读）
            if (!Bukkit.isOwnedByCurrentRegion(new Location(w, x + 0.5, 0, z + 0.5))) continue;
            int y = w.getHighestBlockYAt(x, z);
            if (y > -64 && y < 320) {
                Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
                if (loc.getBlock().getType() == Material.AIR && loc.getBlock().getRelative(0, -1, 0).getType().isSolid())
                    return loc;
            }
        }
        return null;
    }

    /** 找村庄中心附近最近村民作为袭击目标（同区域线程调用） */
    private LivingEntity findNearestVillager(Location center, int radius) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius,
                e2 -> e2 instanceof Villager && !e2.isDead())) {
            double d = e.getLocation().distanceSquared(center);
            if (d < best) { best = d; nearest = (LivingEntity) e; }
        }
        return nearest;
    }

    /** 让怪物主动进攻村庄（设置村民为攻击目标） */
    private void setRaidTarget(LivingEntity mob, LivingEntity target) {
        if (target != null && mob instanceof Mob) {
            ((Mob) mob).setTarget(target);
        }
    }

    private EntityType selectMobType(int doomLevel, int wave) {
        var r = ThreadLocalRandom.current();
        return switch (doomLevel) {
            case 6 -> r.nextBoolean() ? EntityType.VINDICATOR : EntityType.PILLAGER;
            case 7 -> r.nextBoolean() ? EntityType.RAVAGER : EntityType.VINDICATOR;
            case 8 -> r.nextBoolean() ? EntityType.EVOKER : EntityType.RAVAGER;
            case 9, 10 -> r.nextBoolean() ? EntityType.WITCH : EntityType.EVOKER;
            default -> EntityType.PILLAGER;
        };
    }

    private void applyWaveScaling(LivingEntity entity, int doomLevel, int wave, int totalWaves) {
        double scale = 1.0 + (doomLevel - 6) * 0.2 + (double) wave / totalWaves * 0.3;
        entity.setMaxHealth(entity.getMaxHealth() * scale);
        entity.setHealth(entity.getMaxHealth());
    }

    public void spawnWaveMobs(Location center, int doomLevel, int radius, java.util.List<String> mobTypes, RaidState raidState) {
        // 找最近村民作为袭击目标（与原版一致：怪主动进攻村庄）
        LivingEntity villageTarget = findNearestVillager(center, radius);

        // 每波额外生成一个幻术师（和平难度下不可生成，失败不中断整波）
        Location illLoc = findValidSpawnLocation(center, radius);
        if (illLoc != null) {
            try {
                LivingEntity ill = (LivingEntity) illLoc.getWorld().spawnEntity(illLoc, EntityType.ILLUSIONER);
                if (ill != null) {
                    setRaidTarget(ill, villageTarget);
                    mobManager.trackMob(ill);
                    applyWaveScaling(ill, doomLevel, raidState.currentWave, raidState.totalWaves);
                    registerRaidMob(ill, raidState);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 幻术师生成失败：" + e.getMessage());
            }
        }
        for (int i = 0; i < raidState.mobsPerWave; i++) {
            try {
                LivingEntity mob = spawnRaidMob(center, doomLevel, raidState.currentWave, raidState.totalWaves, radius, mobTypes);
                if (mob != null) {
                    setRaidTarget(mob, villageTarget);
                    registerRaidMob(mob, raidState);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 袭击怪生成失败：" + e.getMessage());
            }
        }
        // R10：最后一波生成 Boss
        if (raidState.currentWave >= raidState.totalWaves) {
            spawnBoss(center, doomLevel, radius, raidState);
        }

        // 记录本波实际生成数（含幻术师），供 BossBar 剩余分母使用
        raidState.waveMobCount = raidState.spawnedThisWave;
    }

    /** R10：最后一波生成灾厄首领，标记 raid_boss PDC（供 isRaidBoss 查询） */
    private void spawnBoss(Location center, int doomLevel, int radius, RaidState raidState) {
        org.yinwu.config.EliteConfig elite = configManager.getEliteConfig();
        if (elite == null || !elite.isBossEnabled()) return;

        Location loc = findValidSpawnLocation(center, radius);
        if (loc == null) return;

        LivingEntity boss = null;
        String type = elite.getBossType();
        if (type != null && type.startsWith("mythicmob:")) {
            String id = type.substring("mythicmob:".length()).trim();
            if (mythicMobs != null) boss = mythicMobs.spawnMythicMob(id, loc);
        } else {
            try {
                boss = (LivingEntity) loc.getWorld().spawnEntity(loc, EntityType.valueOf(type.toUpperCase()));
            } catch (Exception ignored) {
                boss = null;
            }
        }
        if (boss == null) return;

        // 血量：基础 × 末波高缩放 × boss 倍率
        double scale = 1.0 + (doomLevel - 6) * 0.2 + 1.0;
        boss.setMaxHealth(boss.getMaxHealth() * scale * elite.getBossHealthMultiplier());
        boss.setHealth(boss.getMaxHealth());
        boss.setPersistent(true);
        boss.setGlowing(true);
        boss.setCustomName("§c§l灾厄首领");
        boss.setCustomNameVisible(true);
        boss.getPersistentDataContainer().set(new NamespacedKey(plugin, "raid_boss"),
            PersistentDataType.BYTE, (byte) 1);

        mobManager.trackMob(boss);
        registerRaidMob(boss, raidState);
        setRaidTarget(boss, findNearestVillager(center, radius));
        if (listener != null) {
            listener.getDefenderManager().startHealthDisplay(boss);
        }
        plugin.getLogger().fine("§c✝ 灾厄首领已降临！类型=" + type + ", 位置=(" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ")");
    }

    /**
     * 将生成实体登记到所属袭击：高亮 + raidMobs 集合（isRaidMob/Forge联动）+ 存活计数 + 本波计数。
     * 必须与 spawnWaveMobs 同一区域线程调用。
     */
    private void registerRaidMob(LivingEntity mob, RaidState raidState) {
        mob.setGlowing(true); // 本波袭击怪高亮
        raidState.raidMobs.add(mob.getUniqueId());
        raidState.aliveMobs.incrementAndGet();
        raidState.spawnedThisWave++;
        // R7：启动怪物血量显示
        if (listener != null) {
            listener.getDefenderManager().startHealthDisplay(mob);
        }
    }
}
