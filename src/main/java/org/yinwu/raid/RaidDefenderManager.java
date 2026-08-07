package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灾厄袭击村庄守卫管理器
 * 负责铁傀儡/村庄守卫的生成和管理
 */
public class RaidDefenderManager {

    private final YinwuRaidPlugin plugin;
    private final ConfigManager configManager;
    private final SpecialRaidListener listener;
    private final RaidMobManager mobManager;

    // 存储每个灾厄信标对应的巨型铁傀儡（信标位置 -> 铁傀儡 UUID）
    private final Map<Location, UUID> beaconDefenders = new ConcurrentHashMap<>();

    // 兼容旧代码：世界名称 -> 铁傀儡 UUID
    private final Map<String, UUID> villageDefenders = new ConcurrentHashMap<>();

    // 铁傀儡生成任务映射
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> golemSpawnTasks = new ConcurrentHashMap<>();

    // 活跃的血量显示任务（生物 UUID -> ScheduledTask）
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> activeHealthTasks = new ConcurrentHashMap<>();

    // 缓存配置值
    private double beaconDefenderFollowRange = 64.0;
    private double giantGolemFollowRange = 48.0;
    private double normalGolemFollowRange = 64.0;
    private double defenderScaleMultiplier = 2.0;

    public RaidDefenderManager(YinwuRaidPlugin plugin, ConfigManager configManager,
                                SpecialRaidListener listener, RaidMobManager mobManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.listener = listener;
        this.mobManager = mobManager;

        // 加载性能配置中的侦测范围
        org.yinwu.config.RaidPerformanceConfig perfConfig = configManager.getRaidPerformanceConfig();
        if (perfConfig != null) {
            Map<String, Double> followRanges = perfConfig.getEntityFollowRange();
            if (followRanges != null) {
                beaconDefenderFollowRange = followRanges.getOrDefault("beacon-defender", 64.0);
                giantGolemFollowRange = followRanges.getOrDefault("giant-golem", 48.0);
                normalGolemFollowRange = followRanges.getOrDefault("normal-golem", 64.0);
            }
        }
        // R12：信标守卫体型倍数读配置
        org.yinwu.config.BeaconDefenderConfig defenderConfig = configManager.getBeaconDefenderConfig();
        if (defenderConfig != null) {
            this.defenderScaleMultiplier = defenderConfig.getScaleMultiplier();
        }
    }

    /**
     * 清理所有守卫资源
     */
    public void cleanup() {
        int cleanedTasks = 0;
        int golemTaskCount = golemSpawnTasks.size();
        int healthTaskCount = activeHealthTasks.size();

        // 取消所有铁傀儡生成任务
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : golemSpawnTasks.values()) {
            try {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                    cleanedTasks++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("§e\u26A0 取消铁傀儡生成任务失败：" + e.getMessage());
            }
        }
        golemSpawnTasks.clear();

        // 取消所有血量显示任务
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : activeHealthTasks.values()) {
            try {
                if (task != null && !task.isCancelled()) {
                    task.cancel();
                    cleanedTasks++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("§e\u26A0 取消血量显示任务失败：" + e.getMessage());
            }
        }
        activeHealthTasks.clear();

        beaconDefenders.clear();
        villageDefenders.clear();

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidDefenderManager] cleanup: 铁傀儡任务=" + golemTaskCount + ", 血量任务=" + healthTaskCount + ", 总共取消=" + cleanedTasks + "个任务");
        }
    }

    // ============== 怪物血量显示 ==============

    /**
     * 启动怪物血量显示任务（每 20 tick 更新自定义名显示血量百分比）。
     * 任务注册进 activeHealthTasks，死亡时由 onEntityDeath 取消。
     */
    public void startHealthDisplay(LivingEntity mob) {
        UUID uuid = mob.getUniqueId();
        if (activeHealthTasks.containsKey(uuid)) return;
        String baseName = "§4§l灾厄" + mobManager.getMobNames().getOrDefault(
            mob.getType().name(), mob.getType().name());
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task =
            mob.getScheduler().runAtFixedRate(plugin, (t) -> {
                if (mob.isDead() || !mob.isValid()) {
                    activeHealthTasks.remove(uuid);
                    t.cancel();
                    return;
                }
                double max = mob.getMaxHealth();
                double hp = mob.getHealth();
                int pct = max > 0 ? (int) Math.round(hp / max * 100) : 0;
                mob.setCustomName(baseName + " §7| §c♥ " + pct + "%");
                mob.setCustomNameVisible(true);
            }, null, 1L, 20L);
        activeHealthTasks.put(uuid, task);
    }

    /** 停止怪物血量显示任务（幂等） */
    public void stopHealthDisplay(UUID uuid) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = activeHealthTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    // ============== 访问器 ==============

    public Map<Location, UUID> getBeaconDefenders() { return beaconDefenders; }
    public Map<String, UUID> getVillageDefenders() { return villageDefenders; }
    public Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> getGolemSpawnTasks() {
        return golemSpawnTasks;
    }
    public Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> getActiveHealthTasks() {
        return activeHealthTasks;
    }

    // ============== 村庄守卫生成 ==============

    /**
     * 生成巨型铁傀儡守卫者
     */
    public void spawnVillageDefender(Location center, int radius, Player triggerPlayer) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidDefenderManager] spawnVillageDefender: 中心=(" + (center != null ? center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() : "null") + "), 半径=" + radius + ", 触发者=" + (triggerPlayer != null ? triggerPlayer.getName() : "null"));
        }

        if (center == null || center.getWorld() == null) {
            plugin.getLogger().fine("§c\u2717 无法生成村庄守护者：位置无效");
            return;
        }

        String worldName = center.getWorld().getName();

        if (villageDefenders.containsKey(worldName)) {
            UUID defenderUuid = villageDefenders.get(worldName);
            if (defenderUuid != null) {
                org.bukkit.entity.Entity existingDefender = center.getWorld().getEntity(defenderUuid);
                if (existingDefender != null && existingDefender.isValid() && !existingDefender.isDead()) {
                    plugin.getLogger().info("§e\u26A0 该村庄已有巨型铁傀儡，不再生成新的铁傀儡");
                    listener.sendRaidActionBar(triggerPlayer, "§e\u26A0 村庄已有巨型铁傀儡守护！");
                    return;
                }
            }
        }

        Bukkit.getRegionScheduler().run(plugin, center, (task) -> {
            Location spawnLocation = mobManager.findValidSpawnLocation(center, radius);
            if (spawnLocation == null) {
                spawnLocation = center.clone().add(0, 6, 0);
                plugin.getLogger().warning("§e\u26A0 未找到理想生成位置，尝试在中心上方6格生成");
            }

            IronGolem defender = (IronGolem) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.IRON_GOLEM);
            if (defender == null) {
                plugin.getLogger().warning("§c\u2717 生成巨型铁傀儡失败！");
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [RaidDefenderManager] spawnVillageDefender: 铁傀儡生成失败");
                }
                return;
            }

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [RaidDefenderManager] spawnVillageDefender: 巨型铁傀儡已生成，UUID=" + defender.getUniqueId() + ", 位置=(" + spawnLocation.getBlockX() + "," + spawnLocation.getBlockY() + "," + spawnLocation.getBlockZ() + ")");
            }

            defender.setAI(true);

            org.yinwu.config.DefenderConfig defenderConfig = configManager.getEntityConfig() != null ?
                configManager.getEntityConfig().getVillageDefender() : null;
            double healthMultiplier = defenderConfig != null ? defenderConfig.getHealthMultiplier() : 3.0;
            double damageMultiplier = defenderConfig != null ? defenderConfig.getDamageMultiplier() : 2.0;
            double speedMultiplier = defenderConfig != null ? defenderConfig.getSpeedMultiplier() : 2.0;
            double scale = defenderScaleMultiplier;

            var maxHealthAttr = defender.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (maxHealthAttr != null) {
                double maxHealth = defender.getMaxHealth() * healthMultiplier;
                maxHealthAttr.setBaseValue(maxHealth);
                defender.setHealth(maxHealth);
            }

            var attackDamageAttr = defender.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            if (attackDamageAttr != null) {
                attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * damageMultiplier);
            }

            var movementSpeedAttr = defender.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
            if (movementSpeedAttr != null) {
                movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * speedMultiplier);
            }

            var followRangeAttr = defender.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            if (followRangeAttr != null) {
                followRangeAttr.setBaseValue(beaconDefenderFollowRange);
            }

            try {
                var scaleAttr = defender.getAttribute(org.bukkit.attribute.Attribute.SCALE);
                if (scaleAttr != null) {
                    scaleAttr.setBaseValue(scale);
                }
            } catch (Exception e) {
                plugin.getLogger().fine("§e\u26A0 当前版本不支持 SCALE 属性，将使用默认大小");
            }

            net.kyori.adventure.text.Component name = net.kyori.adventure.text.Component.text("§b§l村庄守护者 - 巨型铁傀儡");
            defender.customName(name);
            defender.setCustomNameVisible(true);

            setIronGolemTargets(defender);
            villageDefenders.put(worldName, defender.getUniqueId());

            playRaidWarCry(center, radius, defender, triggerPlayer);
            listener.broadcastRaidActionBar(center, radius, "§b§l\u2734 村庄守护者已苏醒！它将保卫村庄！\u2734");
        });
    }

    /**
     * 播放袭击战吼特效
     */
    private void playRaidWarCry(Location center, int radius, IronGolem defender, Player triggerPlayer) {
        center.getWorld().playSound(center, Sound.ITEM_GOAT_HORN_SOUND_1, 1.0f, 1.0f);

        for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius, e2 -> e2 instanceof Player)) {
            Player player = (Player) e;
            if (player.isOnline()) {
                listener.sendRaidActionBar(player, "§4§l\u274C 灾厄袭击开始！§r§e 村庄正在遭受攻击，准备战斗！");
                player.playEffect(player.getLocation(), org.bukkit.Effect.STEP_SOUND, org.bukkit.Material.NETHERITE_BLOCK);
            }
        }
        final io.papermc.paper.threadedregions.scheduler.ScheduledTask[] particleTaskRef =
            new io.papermc.paper.threadedregions.scheduler.ScheduledTask[1];

        particleTaskRef[0] = defender.getScheduler().runAtFixedRate(plugin, t -> {
            if (!defender.isValid() || defender.isDead()) {
                if (particleTaskRef[0] != null) {
                    particleTaskRef[0].cancel();
                }
                return;
            }

            Location loc = defender.getLocation();
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 5, 1.0, 1.0, 1.0, 0.05);
            loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 3, 0.5, 0.5, 0.5, 0.02);
        }, () -> {}, 40L, 20L);

        defender.getScheduler().runDelayed(plugin, t -> {
            if (particleTaskRef[0] != null) {
                particleTaskRef[0].cancel();
            }
        }, () -> {}, 100L);
    }

    /**
     * 启动铁傀儡生成任务
     */
    public void startGolemSpawnTask(Player triggerPlayer, Location center, int radius) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidDefenderManager] startGolemSpawnTask: 触发者=" + triggerPlayer.getName() + ", 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 半径=" + radius + ", 普通铁傀儡数量=4");
        }
        final int NORMAL_GOLEM_COUNT = plugin.getConfig().getInt("raid.defender.golem-count", 4);
        final String worldName = center.getWorld().getName();

        Bukkit.getRegionScheduler().run(plugin, center, (regionTask) -> {
            if (villageDefenders.containsKey(worldName)) {
                UUID defenderUuid = villageDefenders.get(worldName);
                if (defenderUuid != null) {
                    org.bukkit.entity.Entity existingDefender = center.getWorld().getEntity(defenderUuid);
                    if (existingDefender != null && existingDefender.isValid() && !existingDefender.isDead()) {
                        listener.sendRaidActionBar(triggerPlayer, "§e\u26A0 村庄已有巨型铁傀儡守护！");
                        return;
                    }
                }
            }

            spawnVillageDefender(center, radius, triggerPlayer);

            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (delayedTask) -> {
                if (!triggerPlayer.isOnline()) return;

                for (int i = 0; i < NORMAL_GOLEM_COUNT; i++) {
                    final int index = i;
                    Bukkit.getRegionScheduler().run(plugin, center, (spawnTask) -> {
                        Location spawnLocation = mobManager.findValidGolemSpawnLocation(center, (int)(radius * 0.8));
                        if (spawnLocation == null) {
                            plugin.getLogger().warning(String.format("§e\u26A0 找不到第 %d 只铁傀儡的生成位置", index + 1));
                            return;
                        }

                        IronGolem golem = (IronGolem) spawnLocation.getWorld().spawnEntity(spawnLocation, EntityType.IRON_GOLEM);
                        if (golem == null) {
                            plugin.getLogger().warning("§c\u2717 生成铁傀儡失败！");
                            return;
                        }

                        org.yinwu.config.DefenderConfig defenderConfig = configManager.getEntityConfig() != null ?
                            configManager.getEntityConfig().getVillageDefender() : null;
                        double healthMultiplier = defenderConfig != null ? defenderConfig.getHealthMultiplier() : 2.0;
                        double damageMultiplier = defenderConfig != null ? defenderConfig.getDamageMultiplier() : 1.5;
                        double speedMultiplier = defenderConfig != null ? defenderConfig.getSpeedMultiplier() : 2.0;

                        var maxHealthAttr = golem.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                        if (maxHealthAttr != null) {
                            double maxHealth = golem.getMaxHealth() * healthMultiplier;
                            maxHealthAttr.setBaseValue(maxHealth);
                            golem.setHealth(maxHealth);
                        }

                        var attackDamageAttr = golem.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                        if (attackDamageAttr != null) {
                            attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * damageMultiplier);
                        }

                        var movementSpeedAttr = golem.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
                        if (movementSpeedAttr != null) {
                            movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * speedMultiplier);
                        }

                        var followRangeAttr = golem.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
                        if (followRangeAttr != null) {
                            followRangeAttr.setBaseValue(normalGolemFollowRange);
                        }

                        net.kyori.adventure.text.Component name = net.kyori.adventure.text.Component.text("§a§l村庄守卫者 - 铁傀儡");
                        golem.customName(name);
                        golem.setCustomNameVisible(true);

                        setIronGolemTargets(golem);

                        spawnLocation.getWorld().playSound(spawnLocation, Sound.BLOCK_ANVIL_LAND, 1.0f, 1.0f);
                        spawnLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                            spawnLocation.add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.02);
                    });

                    if (i < NORMAL_GOLEM_COUNT - 1) {
                        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> {}, 10L);
                    }
                }
            }, 20L);
        });
    }

    /**
     * 设置铁傀儡 AI 目标
     */
    public void setIronGolemTargets(IronGolem golem) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidDefenderManager] setIronGolemTargets: 铁傀儡UUID=" + golem.getUniqueId() + ", 位置=(" + golem.getLocation().getBlockX() + "," + golem.getLocation().getBlockY() + "," + golem.getLocation().getBlockZ() + ")");
        }
        Bukkit.getRegionScheduler().run(plugin, golem.getLocation(), (task) -> {
            if (golem.isDead() || !golem.isValid()) return;

            var followRangeAttr = golem.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            if (followRangeAttr != null) {
                followRangeAttr.setBaseValue(giantGolemFollowRange);
            }

            golem.getScheduler().runAtFixedRate(plugin, (t) -> {
                if (golem.isDead() || !golem.isValid()) {
                    t.cancel();
                    return;
                }

                Bukkit.getRegionScheduler().run(plugin, golem.getLocation(), (regionTask) -> {
                    if (golem.isDead() || !golem.isValid()) return;

                    Location golemLocation = golem.getLocation();
                    double searchRange = 32.0;

                    LivingEntity raidTarget = null;
                    double nearestDistance = Double.MAX_VALUE;

                    try {
                        Collection<Entity> nearbyEntities = golemLocation.getWorld().getNearbyEntities(
                            golemLocation, searchRange, searchRange, searchRange,
                            entity -> entity instanceof LivingEntity && !entity.isDead()
                        );

                        for (Entity nearby : nearbyEntities) {
                            if (!(nearby instanceof LivingEntity livingEntity)) continue;
                            if (mobManager.getActiveRaidMobs().contains(nearby.getUniqueId())) {
                                double distance = golemLocation.distanceSquared(nearby.getLocation());
                                if (distance < nearestDistance) {
                                    nearestDistance = distance;
                                    raidTarget = livingEntity;
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning(String.format("§e\u26A0 铁傀儡搜索目标失败：%s", e.getMessage()));
                    }

                    if (raidTarget != null && golem instanceof org.bukkit.entity.Mob) {
                        ((org.bukkit.entity.Mob) golem).setTarget(raidTarget);
                    }
                });
            }, null, 40L, 80L);
        });
    }
}
