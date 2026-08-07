package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Blaze;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.util.MythicMobsIntegration;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private final RaidMobSpawner spawner;
    private final RaidMobAI mobAI;

    // 从 config.yml 加载的生物配置（等级 -> 生物列表）
    private final Map<Integer, List<String>> raidMobs = new ConcurrentHashMap<>();

    // 精英怪物配置（等级 -> 精英生物列表）
    private final Map<Integer, List<String>> eliteMobs = new ConcurrentHashMap<>();

    // 怪物中文名称映射
    private final Map<String, String> mobNames = new ConcurrentHashMap<>();

    // 精英怪物配置
    private final Map<Integer, Double> eliteChances = new ConcurrentHashMap<>();
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
        org.yinwu.config.RaidPerformanceConfig perfConfig = configManager.getRaidPerformanceConfig();
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
        org.yinwu.config.DebugConfig debugConfig = configManager.getDebugConfig();
        if (debugConfig != null) {
            spawnLocationDebug = debugConfig.isSpawnLocation();
        }

        this.spawner = new RaidMobSpawner(plugin, this, listener, mythicMobsIntegration);
        this.mobAI = new RaidMobAI(plugin, this, listener);
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
        activeRaidMobs.clear();

        // 清理缓存
        mobSearchOffset.clear();
        lastTargetSearchTime.clear();
        creeperCheckOffset.clear();
        cachedFollowRanges.clear();
        villagerCountCache.clear();
        raidMobs.clear();
        eliteMobs.clear();
        mobNames.clear();
        eliteChances.clear();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidMobManager] cleanup: 初始活跃怪物=" + initialActiveMobs + ", 清理无效UUID=" + invalidUuidCount + ", 搜索偏移量=" + initialSearchOffsets + ", 已清除所有缓存");
        }
    }

    public void reload() {
        raidMobs.clear();
        eliteMobs.clear();
        mobNames.clear();
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
                        mobNames.put(key, namesSection.getString(key));
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

                    List<String> normalMobsList = new ArrayList<>();
                    List<String> eliteMobsList = new ArrayList<>();

                    ConfigurationSection normalSection = levelSection.getConfigurationSection("normal-mobs");
                    if (normalSection != null) {
                        for (String mobType : normalSection.getKeys(false)) {
                            int count = normalSection.getInt(mobType, 1);
                            for (int i = 0; i < count; i++) {
                                normalMobsList.add(mobType);
                            }
                        }
                    }

                    ConfigurationSection eliteSection = levelSection.getConfigurationSection("elite-mobs");
                    if (eliteSection != null) {
                        for (String mobType : eliteSection.getKeys(false)) {
                            int count = eliteSection.getInt(mobType, 1);
                            for (int i = 0; i < count; i++) {
                                eliteMobsList.add(mobType);
                            }
                        }
                    }

                    raidMobs.put(level, normalMobsList);
                    eliteMobs.put(level, eliteMobsList);
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
                    mobNames.put(key, namesSection.getString(key));
                }
            }

            for (int level = 6; level <= 10; level++) {
                String levelKey = "level-" + level;
                ConfigurationSection levelSection = beaconSection.getConfigurationSection(levelKey);
                if (levelSection == null) continue;

                List<String> normalMobsList = new ArrayList<>();
                List<String> eliteMobsList = new ArrayList<>();

                ConfigurationSection normalSection = levelSection.getConfigurationSection("normal-mobs");
                if (normalSection != null) {
                    for (String mobType : normalSection.getKeys(false)) {
                        int count = normalSection.getInt(mobType, 1);
                        for (int i = 0; i < count; i++) {
                            normalMobsList.add(mobType);
                        }
                    }
                }

                ConfigurationSection eliteSection = levelSection.getConfigurationSection("elite-mobs");
                if (eliteSection != null) {
                    for (String mobType : eliteSection.getKeys(false)) {
                        int count = eliteSection.getInt(mobType, 1);
                        for (int i = 0; i < count; i++) {
                            eliteMobsList.add(mobType);
                        }
                    }
                }

                raidMobs.put(level, normalMobsList);
                eliteMobs.put(level, eliteMobsList);
            }

            plugin.getLogger().fine("§a\u2713 灾厄袭击生物配置加载完成");
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c\u2717 加载 config.yml 中的灾厄袭击配置失败", e);
        }
    }


    private final Map<Integer, Integer> mobsPerWave = Map.of(
        6, 15, 7, 10, 8, 15, 9, 20, 10, 25
    );

    public int getMobsPerWave(int doomLevel) {
        // 优先读 raid/config.yml 的 wave-settings.mobs-per-wave 配置
        org.yinwu.config.WaveConfig waveConfig = configManager.getWaveConfig();
        if (waveConfig != null) return waveConfig.getMobsPerWave(doomLevel);
        return mobsPerWave.getOrDefault(doomLevel, 10);
    }

    // ============== 装备系统 ==============

    private void giveEquipmentToMob(LivingEntity mob, int doomLevel) {
        org.yinwu.config.EntityConfig entityConfig = configManager.getEntityConfig();
        org.yinwu.config.EquipmentConfig equipConfig = entityConfig != null ? entityConfig.getEquipment() : null;
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
        org.yinwu.config.EntityConfig entityConfig = configManager.getEntityConfig();
        org.yinwu.config.EquipmentConfig equipConfig = entityConfig != null ? entityConfig.getEquipment() : null;
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
        org.yinwu.config.EntityConfig entityConfig = configManager.getEntityConfig();
        org.yinwu.config.EquipmentConfig equipConfig = entityConfig != null ? entityConfig.getEquipment() : null;
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

    // ============== 怪物名称 ==============

    private String getMobName(EntityType entityType, boolean isElite) {
        String prefix = isElite ? "§c§l[精英] §r" : "";
        String color = isElite ? "§4" : "§e";
        String mobName = mobNames.get(entityType.name());
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

    public void trackMob(org.bukkit.entity.Entity entity) {
        if (entity != null) activeRaidMobs.add(entity.getUniqueId());
    }

    public Map<UUID, Integer> getMobSearchOffset() { return mobSearchOffset; }
    public Map<UUID, Long> getLastTargetSearchTime() { return lastTargetSearchTime; }
    public Map<UUID, Integer> getCreeperCheckOffset() { return creeperCheckOffset; }
    public Map<UUID, Double> getCachedFollowRanges() { return cachedFollowRanges; }
    public Map<String, VillagerCacheEntry> getVillagerCountCache() { return villagerCountCache; }
    public Map<Integer, List<String>> getRaidMobs() { return raidMobs; }
    public Map<Integer, List<String>> getEliteMobs() { return eliteMobs; }
    public Map<String, String> getMobNames() { return mobNames; }
    public Map<Integer, Double> getEliteChances() { return eliteChances; }
    public double getEliteHealthMultiplier() { return eliteHealthMultiplier; }
    public double getEliteDamageMultiplier() { return eliteDamageMultiplier; }
    public double getEliteScaleMultiplier() { return eliteScaleMultiplier; }
    public void setSpawnLocationDebug(boolean val) { this.spawnLocationDebug = val; }
    public void setCreeperDetectionDebug(boolean val) { this.creeperDetectionDebug = val; }

    public Location findValidSpawnLocation(Location center, int radius) { return spawner != null ? spawner.findValidSpawnLocation(center, radius) : null; }
    public Location findValidGolemSpawnLocation(Location center, int radius) { return findValidSpawnLocation(center, radius); }
    public void spawnWaveMobs(Location center, int doomLevel, int radius, java.util.List<String> mobTypes, RaidState raidState) { if (spawner != null) spawner.spawnWaveMobs(center, doomLevel, radius, mobTypes, raidState); }
}
