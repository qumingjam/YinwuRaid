package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.util.MythicMobsIntegration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灾厄袭击生物管理器
 * 负责生物生成、装备、AI/目标设定、苦力怕行为、生物名称映射
 */
public class RaidMobManager {

    private final YinwuRaidPlugin plugin;
    private final ConfigManager configManager;
    private final MythicMobsIntegration mythicMobsIntegration;
    private final SpecialRaidListener listener;

    // 从 config.yml 加载的生物配置（等级 -> 生物列表）
    private final Map<Integer, List<String>> RAID_MOBS = new HashMap<>();

    // 精英怪物配置（等级 -> 精英生物列表）
    private final Map<Integer, List<String>> ELITE_MOBS = new HashMap<>();

    // 怪物中文名称映射
    private final Map<String, String> MOB_NAMES = new HashMap<>();

    // 精英怪物配置
    private final Map<Integer, Double> eliteChances = new HashMap<>();
    private double eliteHealthMultiplier = 1.5;
    private double eliteDamageMultiplier = 1.3;
    private double eliteScaleMultiplier = 1.8;

    // 活跃的灾厄袭击怪物映射（UUID -> 怪物）
    private final Set<UUID> activeRaidMobs = ConcurrentHashMap.newKeySet();

    // 实体属性缓存（UUID -> FOLLOW_RANGE）
    private final Map<UUID, Double> cachedFollowRanges = new ConcurrentHashMap<>();

    // 性能优化：目标搜索时间缓存
    private final Map<UUID, Long> lastTargetSearchTime = new ConcurrentHashMap<>();

    // 性能优化：错峰检索偏移量
    private final Map<UUID, Integer> mobSearchOffset = new ConcurrentHashMap<>();

    // 苦力怕检测偏移量
    private final Map<UUID, Integer> creeperCheckOffset = new ConcurrentHashMap<>();

    // 灾厄生物生成位置缓存
    private final Map<String, Location> spawnLocationCache = new ConcurrentHashMap<>();

    // 缓存位置的方块状态记录
    private final Map<String, String> cachedBlockStates = new ConcurrentHashMap<>();

    // 缓存配置值
    private double beaconDefenderFollowRange = 64.0;
    private double otherMobFollowRange = 48.0;

    // 村民数量缓存
    private final Map<String, VillagerCacheEntry> villagerCountCache = new ConcurrentHashMap<>();

    // 村民缓存条目
    public static class VillagerCacheEntry {
        public int count;
        public long timestamp;
        public VillagerCacheEntry(int count, long timestamp) {
            this.count = count;
            this.timestamp = timestamp;
        }
    }

    // 调试配置
    private boolean spawnLocationDebug = false;
    private boolean creeperDetectionDebug = true;
    private boolean debugEnabled = false;

    // 魔法数字常量
    private static final int MIN_WORLD_HEIGHT = -64;
    private static final int MAX_WORLD_HEIGHT = 320;

    public RaidMobManager(YinwuRaidPlugin plugin, ConfigManager configManager,
                          MythicMobsIntegration mythicMobsIntegration, SpecialRaidListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.mythicMobsIntegration = mythicMobsIntegration;
        this.listener = listener;

        // 加载性能配置中的侦测范围
        ConfigManager.RaidPerformanceConfig perfConfig = configManager.getRaidPerformanceConfig();
        if (perfConfig != null) {
            Map<String, Double> followRanges = perfConfig.getEntityFollowRange();
            if (followRanges != null) {
                beaconDefenderFollowRange = followRanges.getOrDefault("beacon-defender", 64.0);
                otherMobFollowRange = followRanges.getOrDefault("other-mobs", 48.0);
            }
        }

        // 加载调试配置
        debugEnabled = configManager.isDebugEnabled();
        creeperDetectionDebug = true;
        ConfigManager.DebugConfig debugConfig = configManager.getDebugConfig();
        if (debugConfig != null) {
            spawnLocationDebug = debugConfig.isSpawnLocation();
        }

        // 启动周期性任务
        startMobBeaconAttractionTask();
        startCreeperFuseCheckTask();
        startInvalidMobUuidCleanupTask();
    }

    /**
     * 清理所有资源
     */
    public void cleanup() {
        int initialActiveMobs = activeRaidMobs.size();
        int initialSearchOffsets = mobSearchOffset.size();

        // 清理无效的怪物UUID
        int invalidUuidCount = 0;
        Iterator<UUID> mobIterator = activeRaidMobs.iterator();
        while (mobIterator.hasNext()) {
            UUID mobUuid = mobIterator.next();
            org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                mobIterator.remove();
                mobSearchOffset.remove(mobUuid);
                lastTargetSearchTime.remove(mobUuid);
                creeperCheckOffset.remove(mobUuid);
                invalidUuidCount++;
            }
        }

        // 移除所有袭击怪物的自定义名称和发光效果
        for (UUID mobUuid : activeRaidMobs) {
            try {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);
                if (entity instanceof LivingEntity) {
                    LivingEntity livingEntity = (LivingEntity) entity;
                    livingEntity.setCustomName(null);
                    livingEntity.setCustomNameVisible(false);
                    livingEntity.setGlowing(false);
                }
            } catch (Exception ignored) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().fine("Ignored exception: " + ignored.getMessage());
            }
        }
        activeRaidMobs.clear();

        // 清理缓存
        mobSearchOffset.clear();
        lastTargetSearchTime.clear();
        creeperCheckOffset.clear();
        cachedFollowRanges.clear();
        spawnLocationCache.clear();
        cachedBlockStates.clear();
        villagerCountCache.clear();
        RAID_MOBS.clear();
        ELITE_MOBS.clear();
        MOB_NAMES.clear();
        eliteChances.clear();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidMobManager] cleanup: 初始活跃怪物=" + initialActiveMobs + ", 清理无效UUID=" + invalidUuidCount + ", 搜索偏移量=" + initialSearchOffsets + ", 已清除所有缓存");
        }
    }

    public void reload() {
        RAID_MOBS.clear();
        ELITE_MOBS.clear();
        MOB_NAMES.clear();
        eliteChances.clear();
    }

    // ============== 配置加载 ==============

    public void loadRaidMobsConfig() {
        try {
            File raidDir = new File(plugin.getDataFolder(), "raid");

            if (!raidDir.exists()) {
                plugin.getLogger().warning("§e\u26A0 未找到 raid/ 目录，尝试从 config.yml 加载（兼容模式）");
                loadRaidMobsFromConfig();
                return;
            }

            plugin.getLogger().info("§a\u2713 使用模块化配置系统，从 raid/ 目录加载...");

            // 先加载全局配置（由外部调用 loadGlobalSettings）

            // 加载怪物中文名称映射
            File mobNamesFile = new File(raidDir, "mob-names.yml");
            if (mobNamesFile.exists()) {
                YamlConfiguration mobNamesConfig = YamlConfiguration.loadConfiguration(mobNamesFile);
                ConfigurationSection namesSection = mobNamesConfig.getConfigurationSection("mob-names");
                if (namesSection != null) {
                    for (String key : namesSection.getKeys(false)) {
                        MOB_NAMES.put(key, namesSection.getString(key));
                    }
                }
            }

            int loadedLevels = 0;
            for (int level = 6; level <= 10; level++) {
                String levelFileName = "level-" + level + ".yml";
                File levelFile = new File(raidDir, levelFileName);
                if (!levelFile.exists()) continue;

                try {
                    YamlConfiguration levelConfig = YamlConfiguration.loadConfiguration(levelFile);
                    String levelKey = "level-" + level;
                    ConfigurationSection levelSection = levelConfig.getConfigurationSection(levelKey);
                    if (levelSection == null) continue;

                    List<String> normalMobs = new ArrayList<>();
                    List<String> eliteMobs = new ArrayList<>();

                    ConfigurationSection normalSection = levelSection.getConfigurationSection("normal-mobs");
                    if (normalSection != null) {
                        for (String mobType : normalSection.getKeys(false)) {
                            int count = normalSection.getInt(mobType, 1);
                            for (int i = 0; i < count; i++) {
                                normalMobs.add(mobType);
                            }
                        }
                    }

                    ConfigurationSection eliteSection = levelSection.getConfigurationSection("elite-mobs");
                    if (eliteSection != null) {
                        for (String mobType : eliteSection.getKeys(false)) {
                            int count = eliteSection.getInt(mobType, 1);
                            for (int i = 0; i < count; i++) {
                                eliteMobs.add(mobType);
                            }
                        }
                    }

                    RAID_MOBS.put(level, normalMobs);
                    ELITE_MOBS.put(level, eliteMobs);
                    loadedLevels++;
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c\u2717 加载文件 " + levelFileName + " 失败", e);
                }
            }

            plugin.getLogger().fine("§a\u2713 灾厄袭击生物配置加载完成（共 " + loadedLevels + " 个等级）");
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c\u2717 加载 raid/ 目录配置失败", e);
        }
    }

    private void loadRaidMobsFromConfig() {
        try {
            org.bukkit.configuration.ConfigurationSection beaconSection = configManager.getBukkitConfig().getConfigurationSection("beacon");
            if (beaconSection == null) {
                plugin.getLogger().severe("§c\u2717 未找到 beacon 配置节点！");
                return;
            }

            ConfigurationSection namesSection = beaconSection.getConfigurationSection("mob-names");
            if (namesSection != null) {
                for (String key : namesSection.getKeys(false)) {
                    MOB_NAMES.put(key, namesSection.getString(key));
                }
            }

            for (int level = 6; level <= 10; level++) {
                String levelKey = "level-" + level;
                ConfigurationSection levelSection = beaconSection.getConfigurationSection(levelKey);
                if (levelSection == null) continue;

                List<String> normalMobs = new ArrayList<>();
                List<String> eliteMobs = new ArrayList<>();

                ConfigurationSection normalSection = levelSection.getConfigurationSection("normal-mobs");
                if (normalSection != null) {
                    for (String mobType : normalSection.getKeys(false)) {
                        int count = normalSection.getInt(mobType, 1);
                        for (int i = 0; i < count; i++) {
                            normalMobs.add(mobType);
                        }
                    }
                }

                ConfigurationSection eliteSection = levelSection.getConfigurationSection("elite-mobs");
                if (eliteSection != null) {
                    for (String mobType : eliteSection.getKeys(false)) {
                        int count = eliteSection.getInt(mobType, 1);
                        for (int i = 0; i < count; i++) {
                            eliteMobs.add(mobType);
                        }
                    }
                }

                RAID_MOBS.put(level, normalMobs);
                ELITE_MOBS.put(level, eliteMobs);
            }

            plugin.getLogger().fine("§a\u2713 灾厄袭击生物配置加载完成");
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c\u2717 加载 config.yml 中的灾厄袭击配置失败", e);
        }
    }

    // ============== 怪物生成 ==============

    public void spawnWaveMobs(Location center, int doomLevel, int radius, List<String> mobTypes, RaidState raidState) {
        if (mobTypes == null || mobTypes.isEmpty()) {
            plugin.getLogger().warning(String.format("§c [第%d波] \u2717 未找到灾厄等级 %d 的生物列表！", raidState.currentWave, doomLevel));
            return;
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] spawnWaveMobs - center=(%d,%d,%d), doomLevel=%d, wave=%d/%d, totalMobsToSpawn=%d",
                center.getBlockX(), center.getBlockY(), center.getBlockZ(),
                doomLevel, raidState.currentWave, raidState.totalWaves, raidState.mobsPerWave));
        }

        Bukkit.getRegionScheduler().run(plugin, center, (task) -> {
            int spawnedCount = 0;
            int maxPerTick = 4;
            int toSpawn = Math.min(raidState.mobsPerWave - raidState.spawnedThisWave, maxPerTick);

            for (int i = 0; i < toSpawn; i++) {
                Location spawnLocation = findValidSpawnLocation(center, radius);
                if (spawnLocation == null) continue;

                String mobTypeId = mobTypes.get(ThreadLocalRandom.current().nextInt(mobTypes.size()));

                try {
                    LivingEntity mob = spawnMobWithSupport(mobTypeId, spawnLocation);
                    if (mob != null) {
                        listener.recordMobSpawned();
                        enhanceRaidMob(mob, doomLevel);
                        activeRaidMobs.add(mob.getUniqueId());
                        raidState.aliveMobs.incrementAndGet(); // 事件驱动计数器
                        raidState.raidMobs.add(mob.getUniqueId()); // 关联到袭击

                        if (mob.getType() == EntityType.CREEPER) {
                            int offset = ThreadLocalRandom.current().nextInt(100);
                            creeperCheckOffset.put(mob.getUniqueId(), offset);
                        }

                        raidState.spawnedThisWave++;
                        spawnedCount++;
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        String.format("§c [第%d波] \u2717 生成异常", raidState.currentWave), e);
                }
            }

            if (raidState.spawnedThisWave < raidState.mobsPerWave) {
                Bukkit.getRegionScheduler().runDelayed(plugin, center, (nextTask) -> {
                    spawnWaveMobs(center, doomLevel, radius, mobTypes, raidState);
                }, 5L);
            }
        });
    }

    public LivingEntity spawnMobWithSupport(String mobTypeId, Location location) {
        if (mythicMobsIntegration.isMythicMob(mobTypeId)) {
            String mythicMobId = mythicMobsIntegration.extractMythicMobId(mobTypeId);
            LivingEntity mob = mythicMobsIntegration.spawnMythicMob(mythicMobId, location);
            if (configManager.isDebugEnabled()) {
                if (mob != null) {
                    plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] spawnMobWithSupport - mobTypeId=%s, location=(%.1f,%.1f,%.1f), success=true, mythicMobId=%s",
                        mobTypeId, location.getX(), location.getY(), location.getZ(), mythicMobId));
                } else {
                    plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] spawnMobWithSupport - mobTypeId=%s, location=(%.1f,%.1f,%.1f), success=false",
                        mobTypeId, location.getX(), location.getY(), location.getZ()));
                }
            }
            return mob;
        }

        try {
            EntityType entityType = EntityType.valueOf(mobTypeId.toUpperCase());
            LivingEntity mob = (LivingEntity) location.getWorld().spawnEntity(location, entityType);
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] spawnMobWithSupport - mobTypeId=%s, location=(%.1f,%.1f,%.1f), success=%s",
                    mobTypeId, location.getX(), location.getY(), location.getZ(), mob != null ? "true" : "false"));
            }
            return mob;
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("§e\u26A0 无效的生物类型：" + mobTypeId);
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] spawnMobWithSupport - mobTypeId=%s, location=(%.1f,%.1f,%.1f), success=false, invalid mob type",
                    mobTypeId, location.getX(), location.getY(), location.getZ()));
            }
            return null;
        }
    }

    public void enhanceRaidMob(LivingEntity mob, int doomLevel) {
        boolean isElite = false;
        double eliteChance = eliteChances.getOrDefault(doomLevel, 0.0);
        if (eliteChance > 0.0) {
            isElite = ThreadLocalRandom.current().nextDouble() < eliteChance;
        }

        double baseHealthMultiplier = listener.getCachedHealthMultiplier();
        double baseDamageMultiplier = listener.getCachedDamageMultiplier();
        double scaleMultiplier = isElite ? eliteScaleMultiplier : 1.0;
        double doomLevelBonusMultiplier = 1.0 + (doomLevel - 7) * listener.getDoomLevelBonus();
        double eliteHealthBonus = isElite ? eliteHealthMultiplier : 1.0;
        double eliteDamageBonus = isElite ? eliteDamageMultiplier : 1.0;

        double healthMultiplier = baseHealthMultiplier * doomLevelBonusMultiplier * eliteHealthBonus;
        double damageMultiplier = baseDamageMultiplier * doomLevelBonusMultiplier * eliteDamageBonus;
        double speedMultiplier = listener.getCachedSpeedMultiplier();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] enhanceRaidMob - mobType=%s, isElite=%s, doomLevel=%d, healthMultiplier=%.2f, damageMultiplier=%.2f, speedMultiplier=%.2f",
                mob.getType().name(), isElite, doomLevel, healthMultiplier, damageMultiplier, speedMultiplier));
        }

        double maxHealth = mob.getMaxHealth() * healthMultiplier;
        mob.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(maxHealth);
        mob.setHealth(maxHealth);

        var attackDamageAttr = mob.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (attackDamageAttr != null) {
            attackDamageAttr.setBaseValue(attackDamageAttr.getBaseValue() * damageMultiplier);
        }

        var movementSpeedAttr = mob.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED);
        if (movementSpeedAttr != null) {
            movementSpeedAttr.setBaseValue(movementSpeedAttr.getBaseValue() * speedMultiplier);
        }

        try {
            var scaleAttr = mob.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scaleMultiplier);
            }
        } catch (Exception ignored) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().fine("Ignored exception: " + ignored.getMessage());
        }

        String mobName = getMobName(mob.getType(), isElite);
        net.kyori.adventure.text.Component name = net.kyori.adventure.text.Component.text(mobName);
        mob.customName(name);
        mob.setCustomNameVisible(true);

        setMobAITargets(mob);

        Bukkit.getRegionScheduler().runDelayed(plugin, mob.getLocation(), (task) -> {
            if (mob.isValid() && !mob.isDead() && mob instanceof org.bukkit.entity.Mob) {
                org.bukkit.entity.Mob bukkitMob = (org.bukkit.entity.Mob) mob;
                LivingEntity initialTarget = findNearestTarget(mob, true, 1);
                if (initialTarget != null) {
                    bukkitMob.setTarget(initialTarget);
                }
            }
        }, 2L);

        giveEquipmentToMob(mob, doomLevel);

        Bukkit.getRegionScheduler().runDelayed(plugin, mob.getLocation(), (task) -> {
            if (mob.isValid() && !mob.isDead()) {
                try {
                    org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
                    org.bukkit.scoreboard.Team team = scoreboard.getTeam("raid_mob_red");
                    if (team == null) {
                        team = scoreboard.registerNewTeam("raid_mob_red");
                        team.setColor(org.bukkit.ChatColor.RED);
                        team.setAllowFriendlyFire(false);
                    }
                    team.addEntry(mob.getUniqueId().toString());
                    mob.setGlowing(true);
                } catch (Exception e) {
                    mob.setGlowing(true);
                }
            }
        }, 1L);
    }

    // ============== 生成位置搜索 ==============

    public Location findValidSpawnLocation(Location center, int radius) {
        String cacheKey = getCacheKey(center);
        Location cachedLocation = spawnLocationCache.get(cacheKey);

        if (cachedLocation != null) {
            if (isSpawnLocationValid(cachedLocation, cacheKey)) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] findValidSpawnLocation - using cached location at (%.1f,%.1f,%.1f), radius=%d",
                        cachedLocation.getX(), cachedLocation.getY(), cachedLocation.getZ(), radius));
                }
                return cachedLocation;
            } else {
                spawnLocationCache.remove(cacheKey);
                cachedBlockStates.remove(cacheKey);
            }
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        int minY = center.getBlockY() - radius;
        int maxY = center.getBlockY() + radius;

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radius * 0.2 + random.nextDouble() * radius * 0.8;
            int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);
            int highestY = center.getWorld().getHighestBlockYAt(x, z);

            if (highestY < minY || highestY > maxY) continue;
            if (highestY < MIN_WORLD_HEIGHT || highestY > MAX_WORLD_HEIGHT) continue;

            int y = highestY + 1;
            Location feetLoc = new Location(center.getWorld(), x, y - 1, z);
            Location headLoc = new Location(center.getWorld(), x, y, z);

            if (!feetLoc.getBlock().getType().isSolid()) continue;
            String feetBlockType = feetLoc.getBlock().getType().name();
            if (isInvalidSpawnBlock(feetBlockType)) continue;
            if (!canAcceptRedstonePower(feetLoc.getBlock())) continue;
            if (headLoc.getBlock().getType().isSolid()) continue;

            Location aboveLoc = new Location(center.getWorld(), x, y + 1, z);
            if (aboveLoc.getBlock().getType().isSolid()) continue;

            String blockType = feetLoc.getBlock().getType().toString();
            if (blockType.contains("WATER") || blockType.contains("LAVA")) continue;

            Location spawnLoc = new Location(center.getWorld(), x + 0.5, y, z + 0.5);
            spawnLocationCache.put(cacheKey, spawnLoc.clone());
            cachedBlockStates.put(cacheKey + "_feet", feetBlockType);
            cachedBlockStates.put(cacheKey + "_head", headLoc.getBlock().getType().name());

            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] findValidSpawnLocation - found location at attempt=%d, (%.1f,%.1f,%.1f), radius=%d",
                    attempt, spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), radius));
            }
            return spawnLoc;
        }

        // 备用搜索
        for (int i = 0; i < 10; i++) {
            int offsetX = random.nextInt(6) - 3;
            int offsetZ = random.nextInt(6) - 3;
            int x = center.getBlockX() + offsetX;
            int z = center.getBlockZ() + offsetZ;
            int highestY = center.getWorld().getHighestBlockYAt(x, z);

            if (highestY >= minY && highestY <= maxY &&
                highestY >= MIN_WORLD_HEIGHT && highestY <= MAX_WORLD_HEIGHT) {
                int y = highestY + 1;
                Location feetLoc = new Location(center.getWorld(), x, y - 1, z);
                Location headLoc = new Location(center.getWorld(), x, y, z);
                String feetBlockType = feetLoc.getBlock().getType().name();

                if (feetLoc.getBlock().getType().isSolid() &&
                    !headLoc.getBlock().getType().isSolid() &&
                    !isInvalidSpawnBlock(feetBlockType) &&
                    canAcceptRedstonePower(feetLoc.getBlock())) {
                    Location aboveLoc = new Location(center.getWorld(), x, y + 1, z);
                    if (!aboveLoc.getBlock().getType().isSolid()) {
                        Location spawnLoc = new Location(center.getWorld(), x + 0.5, y, z + 0.5);
                        if (configManager.isDebugEnabled()) {
                            plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] findValidSpawnLocation - found in fallback search at (%.1f,%.1f,%.1f), radius=%d",
                                spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), radius));
                        }
                        return spawnLoc;
                    }
                }
            }
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] findValidSpawnLocation - no valid location found after all attempts, radius=%d", radius));
        }
        return null;
    }

    public Location findValidGolemSpawnLocation(Location center, int radius) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int minY = center.getBlockY() - radius;
        int maxY = center.getBlockY() + radius;

        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = radius * 0.2 + random.nextDouble() * radius * 0.8;
            int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
            int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);
            int highestY = center.getWorld().getHighestBlockYAt(x, z);

            if (highestY < minY || highestY > maxY) continue;
            if (highestY < MIN_WORLD_HEIGHT || highestY > MAX_WORLD_HEIGHT) continue;

            int y = highestY + 1;
            Location feetLoc = new Location(center.getWorld(), x, y - 1, z);
            Location headLoc = new Location(center.getWorld(), x, y, z);

            if (!feetLoc.getBlock().getType().isSolid()) continue;
            String feetBlockType = feetLoc.getBlock().getType().name();
            if (isInvalidSpawnBlock(feetBlockType)) continue;
            if (!canAcceptRedstonePower(feetLoc.getBlock())) continue;
            if (headLoc.getBlock().getType().isSolid()) continue;

            Location aboveLoc = new Location(center.getWorld(), x, y + 1, z);
            if (aboveLoc.getBlock().getType().isSolid()) continue;

            String blockType = feetLoc.getBlock().getType().toString();
            if (blockType.contains("WATER") || blockType.contains("LAVA")) continue;

            Location spawnLoc = new Location(center.getWorld(), x + 0.5, y, z + 0.5);
            if (!isSafeSpawnLocation(spawnLoc)) continue;

            return spawnLoc;
        }

        for (int i = 0; i < 10; i++) {
            int offsetX = random.nextInt(6) - 3;
            int offsetZ = random.nextInt(6) - 3;
            int x = center.getBlockX() + offsetX;
            int z = center.getBlockZ() + offsetZ;
            int highestY = center.getWorld().getHighestBlockYAt(x, z);

            if (highestY >= minY && highestY <= maxY &&
                highestY >= MIN_WORLD_HEIGHT && highestY <= MAX_WORLD_HEIGHT) {
                int y = highestY + 1;
                Location feetLoc = new Location(center.getWorld(), x, y - 1, z);
                Location headLoc = new Location(center.getWorld(), x, y, z);
                String feetBlockType = feetLoc.getBlock().getType().name();

                if (feetLoc.getBlock().getType().isSolid() &&
                    !headLoc.getBlock().getType().isSolid() &&
                    !isInvalidSpawnBlock(feetBlockType) &&
                    canAcceptRedstonePower(feetLoc.getBlock())) {
                    Location aboveLoc = new Location(center.getWorld(), x, y + 1, z);
                    if (!aboveLoc.getBlock().getType().isSolid()) {
                        Location spawnLoc = new Location(center.getWorld(), x + 0.5, y, z + 0.5);
                        if (isSafeSpawnLocation(spawnLoc)) {
                            return spawnLoc;
                        }
                    }
                }
            }
        }

        return null;
    }

    public String getCacheKey(Location beaconLocation) {
        return String.format("%s:%d:%d:%d",
            beaconLocation.getWorld().getName(),
            beaconLocation.getBlockX(),
            beaconLocation.getBlockY(),
            beaconLocation.getBlockZ());
    }

    public boolean isSpawnLocationValid(Location cachedLocation, String cacheKey) {
        if (cachedLocation == null || cachedLocation.getWorld() == null) return false;

        Location feetLoc = cachedLocation.clone().subtract(0, 1, 0);
        Location headLoc = cachedLocation.clone();

        String currentFeetType = feetLoc.getBlock().getType().name();
        String currentHeadType = headLoc.getBlock().getType().name();
        String cachedFeetType = cachedBlockStates.get(cacheKey + "_feet");
        String cachedHeadType = cachedBlockStates.get(cacheKey + "_head");

        if (!currentFeetType.equals(cachedFeetType) || !currentHeadType.equals(cachedHeadType)) {
            return false;
        }

        if (!feetLoc.getBlock().getType().isSolid()) return false;
        if (isInvalidSpawnBlock(currentFeetType)) return false;
        if (!canAcceptRedstonePower(feetLoc.getBlock())) return false;
        if (headLoc.getBlock().getType().isSolid()) return false;

        Location aboveLoc = cachedLocation.clone().add(0, 1, 0);
        if (aboveLoc.getBlock().getType().isSolid()) return false;

        String blockType = currentFeetType;
        return !blockType.contains("WATER") && !blockType.contains("LAVA");
    }

    public void clearSpawnLocationCache(Location beaconLocation) {
        if (beaconLocation == null) return;
        String cacheKey = getCacheKey(beaconLocation);
        spawnLocationCache.remove(cacheKey);
        cachedBlockStates.remove(cacheKey + "_feet");
        cachedBlockStates.remove(cacheKey + "_head");
    }

    private boolean isInvalidSpawnBlock(String blockTypeName) {
        if (blockTypeName.contains("GLASS")) return true;
        if (blockTypeName.contains("RAIL")) return true;
        if (blockTypeName.contains("CARPET")) return true;
        if (blockTypeName.contains("PRESSURE_PLATE")) return true;
        if (blockTypeName.contains("TRIPWIRE") || blockTypeName.equals("COBWEB")) return true;
        if (blockTypeName.contains("TRAPDOOR")) return true;
        if (blockTypeName.contains("FENCE_GATE")) return true;
        if (blockTypeName.equals("SNOW")) return true;
        if (blockTypeName.equals("MOSS_CARPET")) return true;
        if (blockTypeName.equals("POWDER_SNOW")) return true;
        return false;
    }

    private boolean canBeRedstonePowered(org.bukkit.block.Block block) {
        return block.isBlockPowered() ||
               block.isBlockIndirectlyPowered() ||
               block.getBlockPower() > 0 ||
               canAcceptRedstonePower(block);
    }

    private boolean canAcceptRedstonePower(org.bukkit.block.Block block) {
        String typeName = block.getType().name();
        if (typeName.equals("AIR") || typeName.equals("CAVE_AIR") || typeName.equals("VOID_AIR") ||
            typeName.contains("WATER") || typeName.contains("LAVA")) {
            return false;
        }
        if (isInvalidSpawnBlock(typeName)) return false;
        return block.getType().isSolid();
    }

    private boolean isSafeSpawnLocation(Location loc) {
        Location feetLoc = loc.clone().subtract(0, 1, 0);
        if (!feetLoc.getBlock().getType().isSolid()) return false;
        String feetBlockType = feetLoc.getBlock().getType().name();
        if (isInvalidSpawnBlock(feetBlockType)) return false;
        if (!canAcceptRedstonePower(feetLoc.getBlock())) return false;
        if (loc.getBlock().getType().isSolid()) return false;
        Location headLoc = loc.clone().add(0, 1, 0);
        if (headLoc.getBlock().getType().isSolid()) return false;
        String blockType = feetLoc.getBlock().getType().toString();
        if (blockType.contains("WATER") || blockType.contains("LAVA")) return false;

        for (int y = 0; y <= 5; y++) {
            Location checkLoc = loc.clone().subtract(0, y, 0);
            String materialName = checkLoc.getBlock().getType().toString();
            if (materialName.equals("BEACON") ||
                materialName.contains("IRON_BLOCK") ||
                materialName.contains("GOLD_BLOCK") ||
                materialName.contains("DIAMOND_BLOCK") ||
                materialName.contains("NETHERITE_BLOCK") ||
                materialName.contains("EMERALD_BLOCK")) {
                return false;
            }
        }
        return true;
    }

    // ============== AI 目标设定 ==============

    public void setMobAITargets(LivingEntity mob) {
        Bukkit.getRegionScheduler().run(plugin, mob.getLocation(), (task) -> {
            if (mob == null || mob.isDead() || !mob.isValid()) return;

            org.bukkit.attribute.AttributeInstance followRangeAttr = mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            if (followRangeAttr != null) {
                followRangeAttr.setBaseValue(listener.getCachedFollowRange());
            }

            if (configManager.isDebugEnabled()) {
                boolean targetVillagers = shouldTargetVillagers(mob);
                int priority = getMobPriority(mob);
                plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] setMobAITargets - mobType=%s, targetVillagers=%s, priority=%d",
                    mob.getType().name(), targetVillagers, priority));
            }

            addVillagerTargetGoal(mob);
        });
    }

    private boolean shouldTargetVillagers(LivingEntity mob) {
        String mobName = mob.getType().name().toLowerCase().replace("_", "-");
        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        if (entityConfig != null && entityConfig.getMobTargets() != null) {
            ConfigManager.MobTargetConfig targetConfig = entityConfig.getMobTargets().get(mobName);
            return targetConfig != null ? targetConfig.isVillagerPriority() : false;
        }
        return false;
    }

    private int getMobPriority(LivingEntity mob) {
        String mobName = mob.getType().name().toLowerCase().replace("_", "-");
        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        if (entityConfig != null && entityConfig.getMobTargets() != null) {
            ConfigManager.MobTargetConfig targetConfig = entityConfig.getMobTargets().get(mobName);
            return targetConfig != null ? targetConfig.getPriority() : 2;
        }
        return 2;
    }

    public int getMobsPerWave(int doomLevel) {
        return switch (doomLevel) {
            case 6 -> 15;
            case 7 -> 10;
            case 8 -> 15;
            case 9 -> 20;
            case 10 -> 25;
            default -> 10;
        };
    }

    // ============== 装备系统 ==============

    private void giveEquipmentToMob(LivingEntity mob, int doomLevel) {
        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        ConfigManager.EquipmentConfig equipConfig = entityConfig != null ? entityConfig.getEquipment() : null;
        if (equipConfig == null || !equipConfig.isEnabled()) return;

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] giveEquipmentToMob - mobType=%s, doomLevel=%d, armorChance=%.2f, weaponChance=%.2f",
                mob.getType().name(), doomLevel,
                equipConfig.getArmorChance() != null ? equipConfig.getArmorChance().getOrDefault(String.valueOf(doomLevel - 6), 0.5) : 0.5,
                equipConfig.getWeaponChance() != null ? equipConfig.getWeaponChance().getOrDefault(String.valueOf(doomLevel - 6), 0.5) : 0.5));
        }

        int configLevel = doomLevel - 6;
        if (configLevel < 1) configLevel = 1;
        if (configLevel > 4) configLevel = 4;

        String levelKey = String.valueOf(configLevel);
        double armorChance = equipConfig.getArmorChance() != null ?
            equipConfig.getArmorChance().getOrDefault(levelKey, 0.5) : 0.5;
        if (ThreadLocalRandom.current().nextDouble() < armorChance) {
            giveArmorToMob(mob, configLevel);
        }

        double weaponChance = equipConfig.getWeaponChance() != null ?
            equipConfig.getWeaponChance().getOrDefault(levelKey, 0.5) : 0.5;
        if (ThreadLocalRandom.current().nextDouble() < weaponChance) {
            giveWeaponToMob(mob, configLevel);
        }
    }

    private void giveArmorToMob(LivingEntity mob, int configLevel) {
        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        ConfigManager.EquipmentConfig equipConfig = entityConfig != null ? entityConfig.getEquipment() : null;
        int enchantLevel = equipConfig != null && equipConfig.getEnchantmentLevels() != null ?
            equipConfig.getEnchantmentLevels().getOrDefault(String.valueOf(configLevel), 2) : 2;

        ItemStack boots = createEnchantedArmor(getBootsForMob(mob), enchantLevel);
        ItemStack leggings = createEnchantedArmor(getLeggingsForMob(mob), enchantLevel);
        ItemStack chestplate = createEnchantedArmor(getChestplateForMob(mob), enchantLevel);
        ItemStack helmet = createEnchantedArmor(getHelmetForMob(mob), enchantLevel);

        mob.getEquipment().setBoots(boots);
        mob.getEquipment().setLeggings(leggings);
        mob.getEquipment().setChestplate(chestplate);
        mob.getEquipment().setHelmet(helmet);

        mob.getEquipment().setBootsDropChance(0.0f);
        mob.getEquipment().setLeggingsDropChance(0.0f);
        mob.getEquipment().setChestplateDropChance(0.0f);
        mob.getEquipment().setHelmetDropChance(0.0f);
    }

    private void giveWeaponToMob(LivingEntity mob, int configLevel) {
        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        ConfigManager.EquipmentConfig equipConfig = entityConfig != null ? entityConfig.getEquipment() : null;
        int enchantLevel = equipConfig != null && equipConfig.getEnchantmentLevels() != null ?
            equipConfig.getEnchantmentLevels().getOrDefault(String.valueOf(configLevel), 2) : 2;

        ItemStack mainHand = createEnchantedWeapon(getMainHandWeaponForMob(mob), enchantLevel);
        ItemStack offHand = getOffHandItemForMob(mob);

        mob.getEquipment().setItemInMainHand(mainHand);
        if (offHand != null) {
            mob.getEquipment().setItemInOffHand(offHand);
        }

        mob.getEquipment().setItemInMainHandDropChance(0.0f);
        if (offHand != null) {
            mob.getEquipment().setItemInOffHandDropChance(0.0f);
        }
    }

    private ItemStack createEnchantedArmor(ItemStack armor, int enchantLevel) {
        if (armor == null || armor.getType() == Material.AIR) return null;

        ItemStack enchanted = armor.clone();
        enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, enchantLevel);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < 0.5) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, Math.min(3, enchantLevel));
        }
        if (random.nextDouble() < 0.3) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.THORNS, Math.min(3, enchantLevel));
        }

        net.kyori.adventure.text.Component name = net.kyori.adventure.text.Component.text(
            "§4§l灾厄" + armor.getType().name().replace("_", " ").toLowerCase()
        );
        enchanted.editMeta(meta -> meta.displayName(name));

        return enchanted;
    }

    private ItemStack createEnchantedWeapon(ItemStack weapon, int enchantLevel) {
        if (weapon == null || weapon.getType() == Material.AIR) return null;

        ItemStack enchanted = weapon.clone();

        if (weapon.getType().toString().contains("SWORD")) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, enchantLevel);
        } else if (weapon.getType().toString().contains("AXE")) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, enchantLevel);
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, Math.min(3, enchantLevel));
        } else if (weapon.getType().toString().contains("BOW")) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, enchantLevel);
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FLAME, 1);
        } else if (weapon.getType().toString().contains("CROSSBOW")) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.MULTISHOT, 1);
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.QUICK_CHARGE, Math.min(3, enchantLevel));
        } else if (weapon.getType().toString().contains("TRIDENT")) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOYALTY, Math.min(3, enchantLevel));
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.CHANNELING, 1);
        } else {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, enchantLevel);
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextDouble() < 0.4) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, Math.min(3, enchantLevel));
        }
        if (random.nextDouble() < 0.3) {
            enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.MENDING, 1);
        }

        net.kyori.adventure.text.Component name = net.kyori.adventure.text.Component.text(
            "§4§l灾厄" + weapon.getType().name().replace("_", " ").toLowerCase()
        );
        enchanted.editMeta(meta -> meta.displayName(name));

        return enchanted;
    }

    private ItemStack getBootsForMob(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIFIED_PIGLIN, PIGLIN_BRUTE -> new ItemStack(Material.NETHERITE_BOOTS);
            case EVOKER, VINDICATOR, ILLUSIONER -> new ItemStack(Material.LEATHER_BOOTS);
            default -> new ItemStack(Material.IRON_BOOTS);
        };
    }

    private ItemStack getLeggingsForMob(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIFIED_PIGLIN, PIGLIN_BRUTE -> new ItemStack(Material.NETHERITE_LEGGINGS);
            case EVOKER, VINDICATOR, ILLUSIONER -> new ItemStack(Material.LEATHER_LEGGINGS);
            default -> new ItemStack(Material.IRON_LEGGINGS);
        };
    }

    private ItemStack getChestplateForMob(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIFIED_PIGLIN, PIGLIN_BRUTE -> new ItemStack(Material.NETHERITE_CHESTPLATE);
            case EVOKER, VINDICATOR, ILLUSIONER -> new ItemStack(Material.LEATHER_CHESTPLATE);
            default -> new ItemStack(Material.IRON_CHESTPLATE);
        };
    }

    private ItemStack getHelmetForMob(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIFIED_PIGLIN, PIGLIN_BRUTE -> new ItemStack(Material.NETHERITE_HELMET);
            case EVOKER, VINDICATOR, ILLUSIONER -> new ItemStack(Material.LEATHER_HELMET);
            default -> new ItemStack(Material.IRON_HELMET);
        };
    }

    private ItemStack getMainHandWeaponForMob(LivingEntity mob) {
        return switch (mob.getType()) {
            case ZOMBIFIED_PIGLIN -> new ItemStack(Material.GOLDEN_AXE);
            case PIGLIN_BRUTE -> new ItemStack(Material.NETHERITE_AXE);
            case VINDICATOR -> new ItemStack(Material.IRON_AXE);
            case EVOKER, ILLUSIONER -> new ItemStack(Material.IRON_SWORD);
            case SKELETON, STRAY -> new ItemStack(Material.BOW);
            case PILLAGER -> new ItemStack(Material.CROSSBOW);
            case DROWNED -> new ItemStack(Material.TRIDENT);
            default -> new ItemStack(Material.IRON_SWORD);
        };
    }

    private ItemStack getOffHandItemForMob(LivingEntity mob) {
        return null;
    }

    // ============== 怪物行为 ==============

    private void addVillagerTargetGoal(LivingEntity mob) {
        Bukkit.getRegionScheduler().run(plugin, mob.getLocation(), (task) -> {
            if (mob == null || mob.isDead() || !mob.isValid()) return;

            boolean targetVillagers = shouldTargetVillagers(mob);
            int priority = getMobPriority(mob);

            org.bukkit.attribute.AttributeInstance followRangeAttr =
                mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            if (followRangeAttr != null) {
                followRangeAttr.setBaseValue(listener.getCachedFollowRange());
            }

            if (mob instanceof org.bukkit.entity.Mob) {
                org.bukkit.entity.Mob bukkitMob = (org.bukkit.entity.Mob) mob;
                bukkitMob.setAI(true);
                try {
                    bukkitMob.setAware(true);
                } catch (Exception ignored) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().fine("Ignored exception: " + ignored.getMessage());
                }
            }

            setupMobBehavior(mob, targetVillagers, priority);
        });
    }

    private void setupMobBehavior(LivingEntity mob, boolean targetVillagers, int priority) {
        final Location raidCenter = getRaidCenterForMob(mob);

        Bukkit.getRegionScheduler().run(plugin, mob.getLocation(), (task) -> {
            if (mob == null || mob.isDead() || !mob.isValid()) return;

            if (mob.getType() == EntityType.ZOMBIFIED_PIGLIN) {
                try {
                    if (mob instanceof org.bukkit.entity.PigZombie) {
                        org.bukkit.entity.PigZombie pigZombie = (org.bukkit.entity.PigZombie) mob;
                        pigZombie.setAnger(Integer.MAX_VALUE);
                        pigZombie.setAggressive(true);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§e\u26A0 设置僵尸猪灵愤怒状态失败：" + e.getMessage());
                }
            }

            if (mob.getType() == EntityType.GHAST) {
                try {
                    var followRangeAttr = mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
                    if (followRangeAttr != null) {
                        followRangeAttr.setBaseValue(64.0);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§e\u26A0 设置恶魂攻击状态失败：" + e.getMessage());
                }
            }

            if (mob.getType() == EntityType.ZOGLIN) {
                try {
                    if (mob instanceof org.bukkit.entity.Mob) {
                        org.bukkit.entity.Mob zoglin = (org.bukkit.entity.Mob) mob;
                        var followRangeAttr = zoglin.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
                        if (followRangeAttr != null) {
                            followRangeAttr.setBaseValue(otherMobFollowRange);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§e\u26A0 设置僵尸疣猪兽攻击状态失败：" + e.getMessage());
                }
            }

            if (mob.getType() == EntityType.BLAZE) {
                try {
                    if (mob instanceof org.bukkit.entity.Blaze) {
                        org.bukkit.entity.Blaze blaze = (org.bukkit.entity.Blaze) mob;
                        var followRangeAttr = blaze.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
                        if (followRangeAttr != null) {
                            followRangeAttr.setBaseValue(otherMobFollowRange);
                        }
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("§e\u26A0 设置烈焰人攻击状态失败：" + e.getMessage());
                }
            }

            if (mob instanceof org.bukkit.entity.Mob) {
                org.bukkit.entity.Mob bukkitMob = (org.bukkit.entity.Mob) mob;
                try {
                    bukkitMob.setAI(true);
                } catch (Exception ignored) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().fine("Ignored exception: " + ignored.getMessage());
                }
            }
        });

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            if (mob.isDead() || !mob.isValid()) {
                task.cancel();
                return;
            }

            UUID mobId = mob.getUniqueId();
            int offsetMs = mobSearchOffset.computeIfAbsent(mobId, k -> ThreadLocalRandom.current().nextInt(3000));
            long currentTime = System.currentTimeMillis();

            if (currentTime % 3000 != offsetMs) return;

            Bukkit.getRegionScheduler().run(plugin, mob.getLocation(), (regionTask) -> {
                if (mob.isDead() || !mob.isValid()) return;

                Long lastSearchTime = lastTargetSearchTime.get(mob.getUniqueId());
                if (lastSearchTime != null && (currentTime - lastSearchTime) < 3000L) return;
                lastTargetSearchTime.put(mob.getUniqueId(), currentTime);

                boolean isEliteMob = isEliteMonster(mob);
                LivingEntity target = findNearestTargetWithPriority(mob, targetVillagers, priority, isEliteMob);

                if (target != null && mob instanceof org.bukkit.entity.Mob) {
                    org.bukkit.entity.Mob bukkitMob = (org.bukkit.entity.Mob) mob;
                    LivingEntity currentTarget = bukkitMob.getTarget();
                    if (currentTarget == null || currentTarget.getUniqueId() != target.getUniqueId()) {
                        bukkitMob.setTarget(target);
                    }
                } else if (mob instanceof org.bukkit.entity.Mob) {
                    moveMobTowardsCenter(mob, raidCenter, isEliteMob);
                }
            });
        }, 1L, 1L);
    }

    private Location getRaidCenterForMob(LivingEntity mob) {
        Location mobLocation = mob.getLocation();

        for (Map.Entry<UUID, RaidState> entry : listener.getRaidStates().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null && player.isOnline()) {
                try {
                    Location playerLoc = player.getLocation();
                    double distance = mobLocation.distance(playerLoc);
                    int villageRadius = listener.getCachedVillageRadius();

                    if (distance <= villageRadius * 2) {
                        return playerLoc.clone();
                    }
                } catch (Exception ignored) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().fine("Ignored exception: " + ignored.getMessage());
                }
            }
        }

        return mobLocation;
    }

    private void moveMobTowardsCenter(LivingEntity mob, Location center, boolean isElite) {
        if (!(mob instanceof org.bukkit.entity.Mob)) return;

        org.bukkit.entity.Mob bukkitMob = (org.bukkit.entity.Mob) mob;
        Location targetLocation = center;

        if (targetLocation == null) {
            String worldName = mob.getWorld().getName();
            Location beaconLocation = listener.getBeaconLocation(worldName);
            if (beaconLocation != null) {
                targetLocation = beaconLocation;
            } else {
                return;
            }
        }

        double distance = mob.getLocation().distance(targetLocation);
        int villageRadius = listener.getCachedVillageRadius();

        LivingEntity nearbyTarget = searchTargetsWithPriority(mob, villageRadius, isElite);
        if (nearbyTarget != null) {
            bukkitMob.setTarget(nearbyTarget);
            return;
        }

        if (distance > 10.0) {
            org.bukkit.util.Vector direction = targetLocation.toVector().subtract(mob.getLocation().toVector()).normalize();
            double targetDistance = Math.min(distance, villageRadius * 0.8);
            Location targetLoc = mob.getLocation().clone().add(direction.multiply(targetDistance));

            try {
                LivingEntity fakeTarget = findOrCreateFakeTarget(mob, targetLoc);
                if (fakeTarget != null) {
                    bukkitMob.setTarget(fakeTarget);
                }
            } catch (Exception e) {
                LivingEntity fakeTarget = findOrCreateFakeTarget(mob, targetLoc);
                if (fakeTarget != null) {
                    bukkitMob.setTarget(fakeTarget);
                }
            }
        }
    }

    private LivingEntity searchTargetsWithPriority(LivingEntity mob, double searchRange, boolean isElite) {
        Location mobLocation = mob.getLocation();
        List<Class<?>> searchOrder;

        if (isElite) {
            searchOrder = Arrays.asList(Villager.class, Player.class, IronGolem.class);
        } else {
            searchOrder = Arrays.asList(IronGolem.class, Player.class, Villager.class);
        }

        for (Class<?> targetType : searchOrder) {
            Collection<Entity> targets = findNearbyEntities(mobLocation, searchRange, (Class<Entity>) targetType);
            if (!targets.isEmpty()) {
                LivingEntity nearestTarget = null;
                double nearestDistance = Double.MAX_VALUE;

                for (Entity target : targets) {
                    if (activeRaidMobs.contains(target.getUniqueId())) continue;
                    if (target instanceof Player player) {
                        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                            player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                            continue;
                        }
                    }
                    double distance = mobLocation.distanceSquared(target.getLocation());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestTarget = (LivingEntity) target;
                    }
                }

                if (nearestTarget != null) return nearestTarget;
            }
        }

        return null;
    }

    private LivingEntity searchVillagersAndGolems(LivingEntity mob, double searchRange) {
        Location mobLocation = mob.getLocation();
        Villager nearestVillager = findNearestEntity(mobLocation, searchRange, Villager.class);
        if (nearestVillager != null) return nearestVillager;
        IronGolem nearestGolem = findNearestEntity(mobLocation, searchRange, IronGolem.class);
        return nearestGolem;
    }

    private LivingEntity findOrCreateFakeTarget(LivingEntity mob, Location targetLoc) {
        Location mobLocation = mob.getLocation();

        Collection<LivingEntity> filtered = new ArrayList<>();
        for (LivingEntity e : findNearbyEntities(mobLocation, 5, LivingEntity.class)) {
            if (!(e instanceof Player)) {
                filtered.add(e);
            }
        }

        if (!filtered.isEmpty()) {
            return filtered.iterator().next();
        }

        LivingEntity villagerOrGolem = findNearestOfMultipleTypes(mobLocation, 10, Villager.class, IronGolem.class);
        if (villagerOrGolem != null) return villagerOrGolem;

        return null;
    }

    private boolean isEliteMonster(LivingEntity mob) {
        try {
            var scaleAttr = mob.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scaleAttr != null) {
                double scale = scaleAttr.getBaseValue();
                return scale >= 1.5;
            }
        } catch (Exception ignored) {
            if (plugin.getConfigManager().isDebugEnabled()) plugin.getLogger().fine("Ignored exception: " + ignored.getMessage());
        }
        return false;
    }

    private double getMobFollowRange(LivingEntity mob) {
        UUID mobUuid = mob.getUniqueId();
        Double cachedRange = cachedFollowRanges.get(mobUuid);
        if (cachedRange != null) return cachedRange;

        try {
            org.bukkit.attribute.AttributeInstance followRangeAttr = mob.getAttribute(org.bukkit.attribute.Attribute.FOLLOW_RANGE);
            if (followRangeAttr != null) {
                double range = followRangeAttr.getBaseValue();
                cachedFollowRanges.put(mobUuid, range);
                return range;
            }
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("§e\u26A0 获取 %s 的 FOLLOW_RANGE 失败：%s",
                mob.getType().name(), e.getMessage()));
        }

        return otherMobFollowRange;
    }

    private LivingEntity findNearestTargetWithPriority(LivingEntity mob, boolean targetVillagers, int priority, boolean isElite) {
        Location mobLocation = mob.getLocation();
        double maxRange = getMobFollowRange(mob);

        List<Class<?>> searchOrder;
        if (isElite) {
            searchOrder = new ArrayList<>();
            if (targetVillagers) searchOrder.add(Villager.class);
            searchOrder.add(Player.class);
            if (targetVillagers) searchOrder.add(IronGolem.class);
        } else {
            searchOrder = new ArrayList<>();
            if (targetVillagers) searchOrder.add(IronGolem.class);
            searchOrder.add(Player.class);
            if (targetVillagers) searchOrder.add(Villager.class);
        }

        for (Class<?> targetClass : searchOrder) {
            LivingEntity target = searchTargetByType(mob, mobLocation, maxRange, targetClass);
            if (target != null) return target;
        }

        return null;
    }

    private LivingEntity searchTargetByType(LivingEntity mob, Location mobLocation, double range, Class<?> targetType) {
        try {
            Collection<Entity> nearbyTargets = findNearbyEntities(mobLocation, range, (Class<Entity>) targetType);
            LivingEntity nearestTarget = null;
            double nearestDistance = Double.MAX_VALUE;

            for (Entity nearby : nearbyTargets) {
                if (!(nearby instanceof LivingEntity livingTarget)) continue;
                if (livingTarget.isDead()) continue;
                if (activeRaidMobs.contains(nearby.getUniqueId())) continue;

                if (nearby instanceof Player player) {
                    if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                        player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                        continue;
                    }
                }

                double distance = mobLocation.distanceSquared(nearby.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestTarget = livingTarget;
                }
            }

            return nearestTarget;
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("§e\u26A0 查找 %s 类型目标时出错：%s",
                targetType.getSimpleName(), e.getMessage()));
            return null;
        }
    }

    public LivingEntity findNearestTarget(LivingEntity mob, boolean targetVillagers, int priority) {
        Location mobLocation = mob.getLocation();
        double maxRange = getMobFollowRange(mob);

        LivingEntity nearestTarget = null;
        double nearestDistance = Double.MAX_VALUE;

        List<Class<?>> targetClasses = new ArrayList<>();
        if (targetVillagers) {
            targetClasses.add(Villager.class);
            targetClasses.add(IronGolem.class);
        }
        targetClasses.add(Player.class);

        for (Class<?> targetClass : targetClasses) {
            try {
                Collection<Entity> nearbyTargets = mobLocation.getWorld().getNearbyEntities(
                    mobLocation, maxRange, maxRange, maxRange,
                    entity -> targetClass.isInstance(entity)
                );

                for (Entity nearby : nearbyTargets) {
                    if (!(nearby instanceof LivingEntity livingTarget)) continue;
                    if (livingTarget.isDead()) continue;
                    if (activeRaidMobs.contains(nearby.getUniqueId())) continue;

                    if (nearby instanceof Player player) {
                        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                            player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                            continue;
                        }
                    }

                    double distance = mobLocation.distanceSquared(nearby.getLocation());
                    if (distance < nearestDistance) {
                        nearestDistance = distance;
                        nearestTarget = livingTarget;
                    }
                }

                if (nearestTarget != null) return nearestTarget;
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("§e\u26A0 查找 %s 类型目标时出错：%s",
                    targetClass.getSimpleName(), e.getMessage()));
            }
        }

        return null;
    }

    // ============== 怪物名称 ==============

    private String getMobName(EntityType entityType, boolean isElite) {
        String prefix = isElite ? "§c§l[精英] §r" : "";
        String color = isElite ? "§4" : "§e";
        String mobName = MOB_NAMES.get(entityType.name());
        if (mobName == null) {
            mobName = getDefaultMobName(entityType);
        }
        return prefix + color + mobName;
    }

    private String getDefaultMobName(EntityType entityType) {
        return switch (entityType) {
            case ZOMBIE -> "灾厄僵尸";
            case SKELETON -> "灾厄骷髅";
            case STRAY -> "灾厄流浪者";
            case SPIDER -> "灾厄蜘蛛";
            case CAVE_SPIDER -> "灾厄洞穴蜘蛛";
            case WITCH -> "灾厄女巫";
            case VINDICATOR -> "灾厄卫道士";
            case PILLAGER -> "灾厄掠夺者";
            case EVOKER -> "灾厄唤魔者";
            case ILLUSIONER -> "灾厄幻术师";
            case RAVAGER -> "灾厄劫掠兽";
            case ZOMBIFIED_PIGLIN -> "灾厄僵尸猪灵";
            case ZOGLIN -> "灾厄僵尸疣猪兽";
            case BLAZE -> "灾厄烈焰人";
            case GHAST -> "灾厄恶魂";
            case DROWNED -> "灾厄溺尸";
            case HUSK -> "灾厄尸壳";
            case VEX -> "灾厄恼鬼";
            default -> "灾厄" + entityType.name();
        };
    }

    // ============== 周期性任务 ==============

    private void startMobBeaconAttractionTask() {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidMobManager] startMobBeaconAttractionTask - triggered, checking " + activeRaidMobs.size() + " active mobs");
        }

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (UUID mobUuid : activeRaidMobs) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);
                if (entity == null || !(entity instanceof LivingEntity) || entity.isDead() || !entity.isValid()) continue;

                LivingEntity mob = (LivingEntity) entity;
                Location mobLoc = mob.getLocation();

                Bukkit.getRegionScheduler().run(plugin, mobLoc, (regionTask) -> {
                    if (mob.isDead() || !mob.isValid()) return;

                    Location nearestBeacon = null;
                    double nearestDistance = Double.MAX_VALUE;

                    for (Location beaconLoc : listener.getBeaconLocations().values()) {
                        if (beaconLoc.getWorld().equals(mobLoc.getWorld())) {
                            double distance = mobLoc.distance(beaconLoc);
                            if (distance < nearestDistance) {
                                nearestDistance = distance;
                                nearestBeacon = beaconLoc;
                            }
                        }
                    }

                    if (nearestBeacon != null && nearestDistance > 100.0) {
                        if (mob instanceof org.bukkit.entity.Mob) {
                            org.bukkit.entity.Mob bukkitMob = (org.bukkit.entity.Mob) mob;
                            if (bukkitMob.getTarget() == null ||
                                bukkitMob.getTarget().getLocation().distance(mobLoc) > 50.0) {
                                moveMobTowardsCenter(mob, nearestBeacon, false);
                            }
                        }
                    }
                });
            }
        }, 600L, 600L);
    }

    private void startCreeperFuseCheckTask() {
        if (configManager.isDebugEnabled()) {
            int creeperCount = 0;
            for (UUID mobUuid : activeRaidMobs) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);
                if (entity instanceof Creeper && !entity.isDead() && entity.isValid()) {
                    creeperCount++;
                }
            }
            plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] startCreeperFuseCheckTask - triggered, activeCreepers=%d", creeperCount));
        }

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (UUID mobUuid : activeRaidMobs) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);
                if (entity == null || !(entity instanceof Creeper) || entity.isDead() || !entity.isValid()) continue;

                Creeper creeper = (Creeper) entity;
                Location creeperLoc = creeper.getLocation();

                int offset = creeperCheckOffset.getOrDefault(mobUuid, -1);
                if (offset == -1) {
                    offset = Math.abs(mobUuid.hashCode()) % 100;
                    creeperCheckOffset.put(mobUuid, offset);
                }

                long currentTimeMs = System.currentTimeMillis();
                boolean shouldCheck = (currentTimeMs % 5000 / 50 == offset);
                if (!shouldCheck) continue;

                Bukkit.getRegionScheduler().run(plugin, creeperLoc, (regionTask) -> {
                    if (creeper.isDead() || !creeper.isValid()) return;

                    LivingEntity target = findNearestOfMultipleTypes(creeperLoc, 4, Villager.class, IronGolem.class);
                    Collection<LivingEntity> nearby = target != null ? Collections.singletonList(target) : Collections.emptyList();

                    int fuseTicks = creeper.getFuseTicks();

                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] startCreeperFuseCheckTask - creeper at (%.1f,%.1f,%.1f), fuseTicks=%d, nearbyTargets=%d",
                            creeperLoc.getX(), creeperLoc.getY(), creeperLoc.getZ(), fuseTicks, nearby.size()));
                    }

                    if (fuseTicks > 0) {
                        if (nearby.isEmpty()) {
                            creeper.setFuseTicks(0);
                        }
                    } else {
                        if (!nearby.isEmpty()) {
                            LivingEntity nearestTarget = null;
                            double nearestDistance = Double.MAX_VALUE;

                            for (LivingEntity nearbyEntity : nearby) {
                                if (nearbyEntity instanceof LivingEntity) {
                                    double distance = creeperLoc.distance(nearbyEntity.getLocation());
                                    if (distance < nearestDistance) {
                                        nearestDistance = distance;
                                        nearestTarget = nearbyEntity;
                                    }
                                }
                            }

                            if (nearestTarget != null) {
                                if (configManager.isDebugEnabled()) {
                                    plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] startCreeperFuseCheckTask - detected target %s at distance=%.2f, triggering fuse",
                                        nearestTarget.getType().name(), nearestDistance));
                                }

                                final LivingEntity finalTarget = nearestTarget;
                                creeper.setTarget(finalTarget);

                                Bukkit.getRegionScheduler().runDelayed(plugin, creeperLoc, (delayTask) -> {
                                    if (creeper.isValid() && !creeper.isDead()) {
                                        Collection<Entity> recheckFiltered = new ArrayList<>();
                                        for (Entity e : findNearbyEntities(creeperLoc, 4, LivingEntity.class)) {
                                            if (e instanceof Villager || e instanceof IronGolem) {
                                                recheckFiltered.add(e);
                                            }
                                        }

                                        if (!recheckFiltered.isEmpty()) {
                                            double currentDistance = creeper.getLocation().distance(finalTarget.getLocation());
                                            if (currentDistance <= 2.5) {
                                                creeper.setFuseTicks(30);
                                                if (configManager.isDebugEnabled()) {
                                                    plugin.getLogger().info(String.format("§e[DEBUG] [RaidMobManager] startCreeperFuseCheckTask - creeper fuse set to 30 ticks, target close enough"));
                                                }
                                            }
                                        }
                                    }
                                }, 20L);
                            }
                        }
                    }
                });
            }
        }, 20L, 20L);
    }

    private void startInvalidMobUuidCleanupTask() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            int removedCount = 0;
            int totalMobs = activeRaidMobs.size();

            Iterator<UUID> iterator = activeRaidMobs.iterator();
            while (iterator.hasNext()) {
                UUID mobUuid = iterator.next();
                org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);

                if (entity == null || entity.isDead() || !entity.isValid()) {
                    iterator.remove();
                    mobSearchOffset.remove(mobUuid);
                    lastTargetSearchTime.remove(mobUuid);
                    creeperCheckOffset.remove(mobUuid);
                    cachedFollowRanges.remove(mobUuid);
                    removedCount++;
                }
            }

            if (removedCount > 0) {
                plugin.getLogger().fine(String.format(
                    "§e\u2734 [CLEANUP] 清理完成：移除了 %d 个无效UUID（剩余：%d / %d）",
                    removedCount, activeRaidMobs.size(), totalMobs));
            }

            cleanupCachedData();
        }, 1200L, 1200L);
    }

    private void cleanupCachedData() {
        Iterator<UUID> iterator = cachedFollowRanges.keySet().iterator();
        while (iterator.hasNext()) {
            UUID mobUuid = iterator.next();
            org.bukkit.entity.Entity entity = Bukkit.getEntity(mobUuid);
            if (entity == null || entity.isDead() || !entity.isValid()) {
                iterator.remove();
            }
        }
    }

    // ============== 手动调试方法 ==============

    public void triggerManualCreeperCheck(Location location) {
        // 由 SpecialRaidListener 调用，直接执行检测
        plugin.getLogger().info("§e\u2734 [MANUAL] 手动触发苦力怕检测！位置：" + location.toString());

        Collection<Creeper> nearbyCreepers = findNearbyEntities(location, 20, Creeper.class);
        if (nearbyCreepers.isEmpty()) {
            plugin.getLogger().warning("§e\u2734 [MANUAL] 附近20格内没有找到苦力怕！");
            return;
        }

        plugin.getLogger().info(String.format("§e\u2734 [MANUAL] 找到 %d 个苦力怕，开始检测...", nearbyCreepers.size()));

        for (Entity entity : nearbyCreepers) {
            Creeper creeper = (Creeper) entity;
            UUID creeperUuid = creeper.getUniqueId();

            if (!activeRaidMobs.contains(creeperUuid)) continue;

            Bukkit.getRegionScheduler().run(plugin, creeper.getLocation(), (task) -> {
                if (creeper.isDead() || !creeper.isValid()) return;

                Location creeperLoc = creeper.getLocation();
                LivingEntity target = findNearestOfMultipleTypes(creeperLoc, 8, Villager.class, IronGolem.class);

                if (target != null) {
                    plugin.getLogger().info("§a\u2713 [MANUAL] 成功检测到目标！苦力怕应该触发自爆！");
                } else {
                    plugin.getLogger().warning("§c\u2717 [MANUAL] 没有检测到任何目标！");
                }
            });
        }
    }

    // ============== 实体检测工具方法 ==============

    @SuppressWarnings("unchecked")
    public <T extends Entity> Collection<T> findNearbyEntities(Location center, double radius, Class<T> entityType) {
        if (center == null || center.getWorld() == null) {
            return Collections.emptyList();
        }

        Collection<T> result = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(
            center, radius, radius, radius,
            entity2 -> entityType.isInstance(entity2) && !entity2.isDead()
        )) {
            result.add(entityType.cast(entity));
        }
        return result;
    }

    public <T extends Entity> T findNearestEntity(Location center, double radius, Class<T> entityType) {
        Collection<T> entities = findNearbyEntities(center, radius, entityType);
        if (entities.isEmpty()) return null;

        T nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (T entity : entities) {
            double distance = center.distanceSquared(entity.getLocation());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = entity;
            }
        }

        return nearest;
    }

    @SafeVarargs
    public final LivingEntity findNearestOfMultipleTypes(
        Location center, double radius, Class<? extends LivingEntity>... entityTypes) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (Class<? extends LivingEntity> entityType : entityTypes) {
            Collection<? extends LivingEntity> entities = findNearbyEntities(center, radius, entityType);
            for (LivingEntity entity : entities) {
                double distance = center.distanceSquared(entity.getLocation());
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = entity;
                }
            }
            if (nearest != null) return nearest;
        }

        return nearest;
    }

    // ============== 访问器 ==============

    public Set<UUID> getActiveRaidMobs() { return activeRaidMobs; }
    public Map<UUID, Integer> getMobSearchOffset() { return mobSearchOffset; }
    public Map<UUID, Long> getLastTargetSearchTime() { return lastTargetSearchTime; }
    public Map<UUID, Integer> getCreeperCheckOffset() { return creeperCheckOffset; }
    public Map<UUID, Double> getCachedFollowRanges() { return cachedFollowRanges; }
    public Map<String, VillagerCacheEntry> getVillagerCountCache() { return villagerCountCache; }
    public Map<Integer, List<String>> getRaidMobs() { return RAID_MOBS; }
    public Map<Integer, List<String>> getEliteMobs() { return ELITE_MOBS; }
    public Map<String, String> getMobNames() { return MOB_NAMES; }
    public Map<Integer, Double> getEliteChances() { return eliteChances; }
    public double getEliteHealthMultiplier() { return eliteHealthMultiplier; }
    public double getEliteDamageMultiplier() { return eliteDamageMultiplier; }
    public double getEliteScaleMultiplier() { return eliteScaleMultiplier; }
    public void setSpawnLocationDebug(boolean val) { this.spawnLocationDebug = val; }
    public void setCreeperDetectionDebug(boolean val) { this.creeperDetectionDebug = val; }

}
