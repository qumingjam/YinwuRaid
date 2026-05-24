package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.util.ConfigUtils;
import org.yinwu.util.PluginLogger;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一配置管理器
 * 集中管理所有配置加载、验证和热重载功能
 */
public class ConfigManager {
    
    private final YinwuRaidPlugin plugin;
    private final PluginLogger logger;
    private final FileConfiguration config;
    
    // ✅ 缓存子配置文件，避免重复IO
    private FileConfiguration cachedRaidConfig;
    private FileConfiguration cachedRewardsConfig;
    
    // 基础配置
    private boolean enabled;
    private String language;
    private boolean checkUpdates;
    private boolean debug;
    
    // 数据库配置
    private DatabaseConfig databaseConfig;
    
    // 袭击系统配置
    private RaidConfig raidConfig;
    
    // 信标配置
    private BeaconConfig beaconConfig;
    
    // 强化系统配置
    private EnhancementConfig enhancementConfig;
    
    // 消息配置
    private MessageConfig messageConfig;
    
    // 性能配置
    private PerformanceConfig performanceConfig;
    
    // Folia配置
    private FoliaConfig foliaConfig;
    
    // ✅ 新增配置
    private EntityConfig entityConfig;
    private RaidPerformanceConfig raidPerformanceConfig;
    private LootConfig lootConfig;
    
    // ✅ 待完成配置（根据 untitled-3.md）
    private WaveConfig waveConfig;
    private EliteConfig eliteConfig;
    private DoomConfig doomConfig;
    
    // ✅ 待添加：信标守护者配置
    private BeaconDefenderConfig beaconDefenderConfig;
    
    // ✅ 调试配置
    private DebugConfig debugConfig;
    
    /**
     * 构造函数：初始化配置管理器
     */
    public ConfigManager(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        this.logger = new PluginLogger(plugin);
        this.config = plugin.getConfig();
        
        // 加载默认配置
        plugin.saveDefaultConfig();
        
        // 加载所有配置模块
        loadAllConfigs();
        
        logger.info("§a配置管理器初始化完成");
    }
    
    /**
     * 加载所有配置模块
     */
    private void loadAllConfigs() {
        loadBaseConfig();
        loadDatabaseConfig();
        loadRaidConfig();
        loadBeaconConfig();
        // ✅ 移除 loadRewardsConfig() - RewardsConfig 未被使用
        // 村民赠礼配置在 EntityConfig 中（从 raid/config.yml 加载）
        // 强化配置在 EnhancementConfig 中（从 rewards/config.yml 加载）
        loadEnhancementConfig();
        loadMessageConfig();
        loadPerformanceConfig();
        loadFoliaConfig();
        // ✅ 加载新增配置
        loadEntityConfig();
        loadRaidPerformanceConfig();
        loadLootConfig();
        // ✅ 加载待完成配置（根据 untitled-3.md）
        loadWaveConfig();
        loadEliteConfig();
        loadDoomConfig();
        // ✅ 添加加载战斗Buff配置
        loadCombatBuffConfig();
        // ✅ 添加加载信标守护者配置
        loadDefenderConfig();
        // ✅ 添加加载调试配置
        loadDebugConfig();
    }
    
    /**
     * 重新加载所有配置（热重载）
     */
    public void reload() {
        if (isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [ConfigManager] reload: 开始重新加载所有配置");
        }
        plugin.reloadConfig();
        // ✅ 清除子配置文件缓存以强制重新加载
        cachedRaidConfig = null;
        cachedRewardsConfig = null;
        loadAllConfigs();
        if (isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [ConfigManager] reload: 所有配置已重新加载完成");
        }
        logger.success("所有配置已重新加载");
    }
    
    /**
     * 获取原始配置对象（用于兼容需要直接访问配置节的场景）
     */
    public FileConfiguration getBukkitConfig() {
        return config;
    }
    
    /**
     * ✅ 加载 raid/config.yml 配置文件（带缓存）
     */
    private FileConfiguration loadRaidConfigFile() {
        if (cachedRaidConfig != null) return cachedRaidConfig;
        File raidConfigFile = new File(plugin.getDataFolder(), "raid/config.yml");
        if (!raidConfigFile.exists()) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [ConfigManager] loadRaidConfigFile: 未找到文件=" + raidConfigFile.getAbsolutePath() + "，使用主 config.yml");
            }
            logger.warning("未找到 raid/config.yml，使用主 config.yml");
            cachedRaidConfig = config;
            return cachedRaidConfig;
        }
        cachedRaidConfig = YamlConfiguration.loadConfiguration(raidConfigFile);
        return cachedRaidConfig;
    }
    
    /**
     * ✅ 加载 rewards/config.yml 配置文件（带缓存）
     */
    private FileConfiguration loadRewardsConfigFile() {
        if (cachedRewardsConfig != null) return cachedRewardsConfig;
        File rewardsConfigFile = new File(plugin.getDataFolder(), "rewards/config.yml");
        if (!rewardsConfigFile.exists()) {
            if (isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [ConfigManager] loadRewardsConfigFile: 未找到文件=" + rewardsConfigFile.getAbsolutePath() + "，使用主 config.yml");
            }
            logger.warning("未找到 rewards/config.yml，使用主 config.yml");
            cachedRewardsConfig = config;
            return cachedRewardsConfig;
        }
        cachedRewardsConfig = YamlConfiguration.loadConfiguration(rewardsConfigFile);
        return cachedRewardsConfig;
    }
    
    // ==================== 基础配置 ====================
    
    private void loadBaseConfig() {
        this.enabled = config.getBoolean("enabled", true);
        this.language = config.getString("language", "zh_CN");
        this.checkUpdates = config.getBoolean("check-updates", true);
        this.debug = config.getBoolean("debug", false);
    }
    
    public boolean isEnabled() { return enabled; }
    public String getLanguage() { return language; }
    public boolean shouldCheckUpdates() { return checkUpdates; }
    public boolean isDebugEnabled() { return debug; }
    
    // ==================== 数据库配置 ====================
    
    private void loadDatabaseConfig() {
        ConfigurationSection dbSection = config.getConfigurationSection("database");
        if (dbSection == null) {
            logger.warning("未找到数据库配置，使用默认值");
            this.databaseConfig = new DatabaseConfig();
            return;
        }
        
        this.databaseConfig = new DatabaseConfig();
        databaseConfig.setType(dbSection.getString("type", "H2"));
        
        ConfigurationSection mysqlSection = dbSection.getConfigurationSection("mysql");
        if (mysqlSection != null) {
            databaseConfig.setMysqlHost(mysqlSection.getString("host", "localhost"));
            databaseConfig.setMysqlPort(mysqlSection.getInt("port", 3306));
            databaseConfig.setMysqlDatabase(mysqlSection.getString("database", "yinwuraids"));
            databaseConfig.setMysqlUsername(mysqlSection.getString("username", "root"));
            databaseConfig.setMysqlPassword(mysqlSection.getString("password", "password"));
            databaseConfig.setMysqlUseSsl(mysqlSection.getBoolean("use-ssl", false));
        }
        
        databaseConfig.setPoolSize(dbSection.getInt("pool-size", 10));
    }
    
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    
    // ==================== 袭击系统配置 ====================
    
    private void loadRaidConfig() {
        ConfigurationSection raidSection = config.getConfigurationSection("raid");
        if (raidSection == null) {
            logger.warning("未找到袭击配置，使用默认值");
            this.raidConfig = new RaidConfig();
            return;
        }
        
        this.raidConfig = new RaidConfig();
        raidConfig.setEnabled(raidSection.getBoolean("enabled", true));
        raidConfig.setCooldown(raidSection.getLong("cooldown", 3600));
        raidConfig.setDetectionRadius(raidSection.getInt("detection-radius", 128));
        raidConfig.setDoomEffectEnabled(raidSection.getBoolean("doom-effect-enabled", true));
        raidConfig.setVillageRadius(raidSection.getInt("village-radius", 48));  // ✅ 新增
        raidConfig.setHeroEffectDuration(raidSection.getInt("hero-effect-duration", 600));  // ✅ 新增
    }
    
    public RaidConfig getRaidConfig() { return raidConfig; }
    
    // ==================== 信标配置 ====================
    
    private void loadBeaconConfig() {
        // ✅ 从 raid/config.yml 加载信标配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection beaconSection = raidConfig.getConfigurationSection("beacon");
        if (beaconSection == null) {
            logger.warning("未找到信标配置，使用默认值");
            this.beaconConfig = new BeaconConfig();
            return;
        }
        
        this.beaconConfig = new BeaconConfig();
        beaconConfig.setEnabled(beaconSection.getBoolean("enabled", true));
        beaconConfig.setMaxRange(beaconSection.getInt("max-range", 50));
        beaconConfig.setDoomEffectDuration(beaconSection.getInt("doom-effect-duration", 300));
        
        // 加载检测配置
        ConfigurationSection detectionSection = beaconSection.getConfigurationSection("detection");
        if (detectionSection != null) {
            beaconConfig.setRequireContainer(detectionSection.getBoolean("require-container", true));
            beaconConfig.setContainerTypes(detectionSection.getStringList("container-types"));
        }
        
        // 加载层级配置
        ConfigurationSection layersSection = beaconSection.getConfigurationSection("layers");
        if (layersSection != null) {
            Map<Integer, LayerConfig> layers = new HashMap<>();
            for (String key : layersSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    ConfigurationSection layerSection = layersSection.getConfigurationSection(key);
                    if (layerSection != null) {
                        LayerConfig layerConfig = new LayerConfig();
                        layerConfig.setMaterial(layerSection.getString("material", "IRON_BLOCK"));
                        layerConfig.setSize(layerSection.getInt("size", 3));
                        layerConfig.setOffset(layerSection.getInt("offset", 1));
                        layers.put(level, layerConfig);
                    }
                } catch (NumberFormatException e) {
                    logger.warning("无效的信标层级配置: " + key);
                }
            }
            beaconConfig.setLayers(layers);
        }
        
        // 加载灾厄等级映射
        ConfigurationSection doomLevelsSection = beaconSection.getConfigurationSection("doom-levels");
        if (doomLevelsSection != null) {
            Map<Integer, Integer> doomLevels = new HashMap<>();
            for (String key : doomLevelsSection.getKeys(false)) {
                try {
                    int beaconLevel = Integer.parseInt(key);
                    int doomLevel = doomLevelsSection.getInt(key);
                    doomLevels.put(beaconLevel, doomLevel);
                } catch (NumberFormatException e) {
                    logger.warning("无效的灾厄等级映射: " + key);
                }
            }
            beaconConfig.setDoomLevels(doomLevels);
        }
        
        // 加载激活配置
        ConfigurationSection activationSection = beaconSection.getConfigurationSection("activation");
        if (activationSection != null) {
            ActivationConfig activationConfig = new ActivationConfig();
            activationConfig.setMaterial(activationSection.getString("material", "NETHER_STAR"));
            activationConfig.setAmount(activationSection.getInt("amount", 1));
            activationConfig.setDisplayName(activationSection.getString("display-name", "下界之星"));
            beaconConfig.setActivationConfig(activationConfig);
        }
        
        // 加载彩蛋级激活配置
        ConfigurationSection easterEggActivationSection = beaconSection.getConfigurationSection("easter-egg-activation");
        if (easterEggActivationSection != null) {
            ActivationConfig easterEggConfig = new ActivationConfig();
            easterEggConfig.setMaterial(easterEggActivationSection.getString("material", "OMINOUS_BOTTLE"));
            easterEggConfig.setAmount(easterEggActivationSection.getInt("amount", 64));
            easterEggConfig.setDisplayName(easterEggActivationSection.getString("display-name", "不祥之瓶"));
            beaconConfig.setEasterEggActivationConfig(easterEggConfig);
        }
    }
    
    public BeaconConfig getBeaconConfig() { return beaconConfig; }
    
    // ==================== 强化系统配置 ====================
    
    private void loadEnhancementConfig() {
        // ✅ 从 rewards/config.yml 加载强化配置
        FileConfiguration rewardsConfig = loadRewardsConfigFile();
        ConfigurationSection enhancementSection = rewardsConfig.getConfigurationSection("enhancement");
        if (enhancementSection == null) {
            logger.warning("未找到强化系统配置，使用默认值");
            this.enhancementConfig = new EnhancementConfig();
            return;
        }
        
        this.enhancementConfig = new EnhancementConfig();
        enhancementConfig.setMaxEnhanceCount(enhancementSection.getInt("max-enhance-count", 3));
        enhancementConfig.setBlacklist(enhancementSection.getStringList("blacklist"));
        
        // 加载附魔上限
        ConfigurationSection limitsSection = enhancementSection.getConfigurationSection("limits");
        if (limitsSection != null) {
            Map<String, Integer> limits = new HashMap<>();
            for (String key : limitsSection.getKeys(false)) {
                limits.put(key, limitsSection.getInt(key));
            }
            enhancementConfig.setLimits(limits);
        }
        
        // ✅ 加载灾厄之种概率配置
        ConfigurationSection seedsSection = enhancementSection.getConfigurationSection("seeds");
        if (seedsSection != null) {
            Map<String, Map<Integer, Double>> seedChances = new HashMap<>();
            Map<String, String> seedNames = new HashMap<>();
            Map<String, SeedBookConfig> seedBooks = new HashMap<>();
            for (String seedKey : seedsSection.getKeys(false)) {
                ConfigurationSection seedConfig = seedsSection.getConfigurationSection(seedKey);
                if (seedConfig != null) {
                    // 保存种子名称用于按名称匹配
                    String seedName = seedConfig.getString("name", "");
                    seedNames.put(seedKey, seedName);
                    
                    // 加载成书配置
                    ConfigurationSection bookSection = seedConfig.getConfigurationSection("book");
                    if (bookSection != null) {
                        SeedBookConfig bookConfig = new SeedBookConfig();
                        bookConfig.setTitle(bookSection.getString("title", ""));
                        bookConfig.setAuthor(bookSection.getString("author", ""));
                        bookConfig.setPages(bookSection.getStringList("pages"));
                        seedBooks.put(seedKey, bookConfig);
                    }
                    
                    ConfigurationSection chancesSection = seedConfig.getConfigurationSection("chances");
                    if (chancesSection != null) {
                        Map<Integer, Double> chances = new HashMap<>();
                        for (String countKey : chancesSection.getKeys(false)) {
                            try {
                                int count = Integer.parseInt(countKey);
                                double probability = chancesSection.getDouble(countKey);
                                chances.put(count, probability);
                            } catch (NumberFormatException e) {
                                logger.warning("无效的种子强化次数配置: " + countKey);
                            }
                        }
                        seedChances.put(seedKey, chances);
                    }
                }
            }
            enhancementConfig.setSeedChances(seedChances);
            enhancementConfig.setSeedNames(seedNames);
            enhancementConfig.setSeedBooks(seedBooks);
            logger.info("§a✓ 已加载 " + seedChances.size() + " 种灾厄之种概率配置");
        } else {
            logger.fine("§eℹ 未配置灾厄之种概率，将使用默认值");
            enhancementConfig.setSeedChances(new HashMap<>());
            enhancementConfig.setSeedNames(new HashMap<>());
        }
        
        // ✅ 加载附魔数量配置
        ConfigurationSection enchantCountsSection = enhancementSection.getConfigurationSection("enchant-counts");
        if (enchantCountsSection != null) {
            Map<String, Map<Integer, Integer>> enchantCounts = new HashMap<>();
            for (String professionKey : enchantCountsSection.getKeys(false)) {
                ConfigurationSection profSection = enchantCountsSection.getConfigurationSection(professionKey);
                if (profSection != null) {
                    Map<Integer, Integer> levelMap = new HashMap<>();
                    for (String levelKey : profSection.getKeys(false)) {
                        try {
                            int heroLevel = Integer.parseInt(levelKey);
                            int count = profSection.getInt(levelKey);
                            levelMap.put(heroLevel, count);
                        } catch (NumberFormatException e) {
                            logger.warning("无效的附魔数量等级配置: " + levelKey);
                        }
                    }
                    enchantCounts.put(professionKey, levelMap);
                }
            }
            enhancementConfig.setEnchantCounts(enchantCounts);
            logger.fine("§a✓ 已加载 " + enchantCounts.size() + " 种职业的附魔数量配置");
        } else {
            enhancementConfig.setEnchantCounts(new HashMap<>());
            logger.fine("§eℹ 未配置附魔数量，将使用默认值");
        }
    }
    
    public EnhancementConfig getEnhancementConfig() { return enhancementConfig; }
    
    // ==================== 消息配置 ====================
    
    private void loadMessageConfig() {
        ConfigurationSection messagesSection = config.getConfigurationSection("messages");
        if (messagesSection == null) {
            logger.warning("未找到消息配置，使用默认值");
            this.messageConfig = new MessageConfig();
            return;
        }
        
        this.messageConfig = new MessageConfig();
        messageConfig.setPrefixEnabled(messagesSection.getBoolean("prefix-enabled", true));
        messageConfig.setPrefix(messagesSection.getString("prefix", "§6§l[YinwuRaid] §r"));
        messageConfig.setSoundEnabled(messagesSection.getBoolean("sound-enabled", true));
        messageConfig.setActionbarEnabled(messagesSection.getBoolean("actionbar-enabled", true));
        messageConfig.setTitleEnabled(messagesSection.getBoolean("title-enabled", true));
    }
    
    public MessageConfig getMessageConfig() { return messageConfig; }
    
    // ==================== 性能配置 ====================
    
    private void loadPerformanceConfig() {
        ConfigurationSection perfSection = config.getConfigurationSection("performance");
        if (perfSection == null) {
            logger.warning("未找到性能配置，使用默认值");
            this.performanceConfig = new PerformanceConfig();
            return;
        }
        
        this.performanceConfig = new PerformanceConfig();
        performanceConfig.setAsyncTasks(perfSection.getBoolean("async-tasks", true));
        performanceConfig.setEntitySpawnLimit(perfSection.getInt("entity-spawn-limit", 50));
        performanceConfig.setParticleLimit(perfSection.getInt("particle-limit", 100));
        performanceConfig.setCacheEnabled(perfSection.getBoolean("cache-enabled", true));
        performanceConfig.setCacheExpiry(perfSection.getLong("cache-expiry", 300));
    }
    
    public PerformanceConfig getPerformanceConfig() { return performanceConfig; }
    
    // ==================== Folia配置 ====================
    
    private void loadFoliaConfig() {
        ConfigurationSection foliaSection = config.getConfigurationSection("folia");
        if (foliaSection == null) {
            logger.warning("未找到Folia配置，使用默认值");
            this.foliaConfig = new FoliaConfig();
            return;
        }
        
        this.foliaConfig = new FoliaConfig();
        foliaConfig.setEnabled(foliaSection.getBoolean("enabled", true));
        foliaConfig.setRegionScheduler(foliaSection.getBoolean("region-scheduler", true));
        foliaConfig.setEntityThreadGroup(foliaSection.getBoolean("entity-thread-group", true));
    }
    
    public FoliaConfig getFoliaConfig() { return foliaConfig; }
    
    // ==================== ✅ 新增配置加载方法 ====================
    
    /**
     * ✅ 加载实体配置
     */
    private void loadEntityConfig() {
        // ✅ 从 raid/config.yml 加载实体配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection entitySection = raidConfig.getConfigurationSection("entities");
        if (entitySection == null) {
            logger.warning("未找到实体配置，使用默认值");
            this.entityConfig = new EntityConfig();
            this.entityConfig.setHealthMultiplier(2.0);
            this.entityConfig.setDamageMultiplier(1.5);
            this.entityConfig.setSpeedMultiplier(1.2);
            this.entityConfig.setFollowRange(48);
            
            // 创建默认的村民赠礼配置
            VillagerGiftConfig defaultGiftConfig = new VillagerGiftConfig();
            defaultGiftConfig.setRange(5.0);
            defaultGiftConfig.setMinCooldown(30);
            defaultGiftConfig.setMaxCooldown(180);
            defaultGiftConfig.setDetectionInterval(100);
            this.entityConfig.setVillagerGift(defaultGiftConfig);
            return;
        }
        
        this.entityConfig = new EntityConfig();
        entityConfig.setHealthMultiplier(entitySection.getDouble("health-multiplier", 2.0));
        entityConfig.setDamageMultiplier(entitySection.getDouble("damage-multiplier", 1.5));
        entityConfig.setSpeedMultiplier(entitySection.getDouble("speed-multiplier", 1.2));
        entityConfig.setFollowRange(entitySection.getInt("follow-range", 48));
        
        // 加载村民赠礼配置
        ConfigurationSection giftSection = entitySection.getConfigurationSection("villager-gift");
        if (giftSection != null) {
            VillagerGiftConfig giftConfig = new VillagerGiftConfig();
            giftConfig.setRange(giftSection.getDouble("range", 5.0));
            giftConfig.setMinCooldown(giftSection.getInt("min-cooldown", 30));
            giftConfig.setMaxCooldown(giftSection.getInt("max-cooldown", 180));
            giftConfig.setDetectionInterval(giftSection.getLong("detection-interval", 100));
            entityConfig.setVillagerGift(giftConfig);
        } else {
            // 创建默认的村民赠礼配置
            VillagerGiftConfig defaultGiftConfig = new VillagerGiftConfig();
            defaultGiftConfig.setRange(5.0);
            defaultGiftConfig.setMinCooldown(30);
            defaultGiftConfig.setMaxCooldown(180);
            defaultGiftConfig.setDetectionInterval(100);
            entityConfig.setVillagerGift(defaultGiftConfig);
        }
        
        // 加载村庄守护者配置
        ConfigurationSection defenderSection = entitySection.getConfigurationSection("village-defender");
        if (defenderSection != null) {
            DefenderConfig defenderConfig = new DefenderConfig();
            defenderConfig.setHealthMultiplier(defenderSection.getDouble("health-multiplier", 3.0));
            defenderConfig.setDamageMultiplier(defenderSection.getDouble("damage-multiplier", 2.0));
            defenderConfig.setSpeedMultiplier(defenderSection.getDouble("speed-multiplier", 2.0));
            entityConfig.setVillageDefender(defenderConfig);
        }
        
        // 加载怪物目标配置
        ConfigurationSection targetsSection = entitySection.getConfigurationSection("mob-targets");
        if (targetsSection != null) {
            Map<String, MobTargetConfig> mobTargets = new HashMap<>();
            for (String mobName : targetsSection.getKeys(false)) {
                ConfigurationSection mobSection = targetsSection.getConfigurationSection(mobName);
                if (mobSection != null) {
                    MobTargetConfig targetConfig = new MobTargetConfig();
                    targetConfig.setVillagerPriority(mobSection.getBoolean("villager-priority", false));
                    targetConfig.setPriority(mobSection.getInt("priority", 2));
                    mobTargets.put(mobName, targetConfig);
                }
            }
            entityConfig.setMobTargets(mobTargets);
        }
        
        // 加载装备配置
        ConfigurationSection equipSection = entitySection.getConfigurationSection("equipment");
        if (equipSection != null) {
            EquipmentConfig equipConfig = new EquipmentConfig();
            equipConfig.setEnabled(equipSection.getBoolean("enabled", true));
            
            // 加载护甲概率
            ConfigurationSection armorSection = equipSection.getConfigurationSection("armor-chance");
            if (armorSection != null) {
                Map<String, Double> armorChance = new HashMap<>();
                for (String key : armorSection.getKeys(false)) {
                    armorChance.put(key, armorSection.getDouble(key, 0.5));
                }
                equipConfig.setArmorChance(armorChance);
            }
            
            // 加载武器概率
            ConfigurationSection weaponSection = equipSection.getConfigurationSection("weapon-chance");
            if (weaponSection != null) {
                Map<String, Double> weaponChance = new HashMap<>();
                for (String key : weaponSection.getKeys(false)) {
                    weaponChance.put(key, weaponSection.getDouble(key, 0.5));
                }
                equipConfig.setWeaponChance(weaponChance);
            }
            
            // 加载附魔等级
            ConfigurationSection enchantSection = equipSection.getConfigurationSection("enchantment-levels");
            if (enchantSection != null) {
                Map<String, Integer> enchantLevels = new HashMap<>();
                for (String key : enchantSection.getKeys(false)) {
                    enchantLevels.put(key, enchantSection.getInt(key, 2));
                }
                equipConfig.setEnchantmentLevels(enchantLevels);
            }
            
            entityConfig.setEquipment(equipConfig);
        }
    }
    
    public EntityConfig getEntityConfig() { return entityConfig; }
    
    /**
     * ✅ 加载袭击性能配置
     */
    private void loadRaidPerformanceConfig() {
        ConfigurationSection perfSection = config.getConfigurationSection("raid-performance");
        if (perfSection == null) {
            logger.warning("未找到袭击性能配置，使用默认值");
            this.raidPerformanceConfig = new RaidPerformanceConfig();
            this.raidPerformanceConfig.setVillagerCheckInterval(60);
            this.raidPerformanceConfig.setBossBarUpdateInterval(20);
            this.raidPerformanceConfig.setFogEffectInterval(10);
            this.raidPerformanceConfig.setFogParticleCount(30);
            this.raidPerformanceConfig.setBuffApplyInterval(200);
            this.raidPerformanceConfig.setRaidSchedulerInterval(60);
            this.raidPerformanceConfig.setMaxPlayerCheckCount(50);
            return;
        }
        
        this.raidPerformanceConfig = new RaidPerformanceConfig();
        raidPerformanceConfig.setVillagerCheckInterval(perfSection.getLong("villager-check-interval", 60));
        raidPerformanceConfig.setBossBarUpdateInterval(perfSection.getLong("bossbar-update-interval", 20));
        raidPerformanceConfig.setFogEffectInterval(perfSection.getLong("fog-effect-interval", 10));
        raidPerformanceConfig.setFogParticleCount(perfSection.getInt("fog-particle-count", 30));
        raidPerformanceConfig.setBuffApplyInterval(perfSection.getLong("buff-apply-interval", 200));
        raidPerformanceConfig.setRaidSchedulerInterval(perfSection.getLong("raid-scheduler-interval", 60));
        raidPerformanceConfig.setMaxPlayerCheckCount(perfSection.getInt("max-player-check-count", 50));
        
        // 加载实体侦测范围
        ConfigurationSection entitySection = perfSection.getConfigurationSection("entity-follow-range");
        if (entitySection != null) {
            Map<String, Double> followRanges = new HashMap<>();
            for (String key : entitySection.getKeys(false)) {
                followRanges.put(key, entitySection.getDouble(key, 48.0));
            }
            raidPerformanceConfig.setEntityFollowRange(followRanges);
        }
        
        // 加载Title显示时间配置
        ConfigurationSection titleSection = perfSection.getConfigurationSection("title-timing");
        if (titleSection != null) {
            ConfigurationSection failSection = titleSection.getConfigurationSection("failure");
            if (failSection != null) {
                TitleTimingConfig titleConfig = new TitleTimingConfig();
                titleConfig.setFirstFadeIn(failSection.getInt("first-fade-in", 10));
                titleConfig.setFirstDisplay(failSection.getInt("first-display", 60));
                titleConfig.setFirstFadeOut(failSection.getInt("first-fade-out", 20));
                titleConfig.setSecondFadeIn(failSection.getInt("second-fade-in", 10));
                titleConfig.setSecondDisplay(failSection.getInt("second-display", 100));
                titleConfig.setSecondFadeOut(failSection.getInt("second-fade-out", 20));
                titleConfig.setDelayBetween(failSection.getLong("delay-between", 80));
                raidPerformanceConfig.setTitleTiming(titleConfig);
            }
        }
    }
    
    public RaidPerformanceConfig getRaidPerformanceConfig() { return raidPerformanceConfig; }
    
    /**
     * ✅ 加载战利品配置
     */
    private void loadLootConfig() {
        // ✅ 从 raid/config.yml 加载战利品配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection lootSection = raidConfig.getConfigurationSection("raid-loot");
        if (lootSection == null) {
            logger.warning("未找到战利品配置，使用默认值");
            this.lootConfig = new LootConfig();
            this.lootConfig.setEmeraldBaseAmount(10);
            this.lootConfig.setExpBottleMultiplier(2);
            this.lootConfig.setEnchantedGoldenAppleChance(0.3);
            this.lootConfig.setPerLevelLoot(new HashMap<>());
            return;
        }
        
        this.lootConfig = new LootConfig();
        
        ConfigurationSection baseSection = lootSection.getConfigurationSection("base-rewards");
        if (baseSection != null) {
            lootConfig.setEmeraldBaseAmount(baseSection.getInt("emerald-base", 10));
            lootConfig.setExpBottleMultiplier(baseSection.getInt("exp-bottle-multiplier", 2));
        }
        
        ConfigurationSection extraSection = lootSection.getConfigurationSection("extra-items");
        if (extraSection != null) {
            ConfigurationSection appleSection = extraSection.getConfigurationSection("enchanted-golden-apple");
            if (appleSection != null) {
                lootConfig.setEnchantedGoldenAppleChance(appleSection.getDouble("chance", 0.3));
            }
        }
        
        // ✅ 加载各难度专属战利品
        ConfigurationSection perLevelSection = lootSection.getConfigurationSection("per-level");
        if (perLevelSection != null) {
            Map<Integer, List<LevelLootEntry>> perLevelLoot = new HashMap<>();
            for (String levelKey : perLevelSection.getKeys(false)) {
                try {
                    int doomLevel = Integer.parseInt(levelKey);
                    List<Map<?, ?>> rawEntries = perLevelSection.getMapList(levelKey);
                    List<LevelLootEntry> entries = new java.util.ArrayList<>();
                    for (Map<?, ?> rawEntry : rawEntries) {
                        LevelLootEntry entry = new LevelLootEntry();
                        Object materialObj = rawEntry.get("material");
                        entry.setMaterial(materialObj != null ? materialObj.toString() : "AIR");
                        Object amountObj = rawEntry.get("amount");
                        entry.setAmount(amountObj instanceof Number ? ((Number) amountObj).intValue() : 1);
                        Object enchantObj = rawEntry.get("enchant");
                        if (enchantObj != null) {
                            entry.setEnchant(enchantObj.toString());
                        }
                        Object enchantLevelObj = rawEntry.get("enchant-level");
                        if (enchantLevelObj instanceof Number) {
                            entry.setEnchantLevel(((Number) enchantLevelObj).intValue());
                        }
                        Object seedTypeObj = rawEntry.get("seed-type");
                        if (seedTypeObj instanceof Number) {
                            entry.setSeedType(((Number) seedTypeObj).intValue());
                        }
                        entries.add(entry);
                    }
                    perLevelLoot.put(doomLevel, entries);
                } catch (NumberFormatException e) {
                    logger.warning("无效的战利品等级配置: " + levelKey);
                }
            }
            lootConfig.setPerLevelLoot(perLevelLoot);
            logger.fine("§a✓ 已加载 " + perLevelLoot.size() + " 个难度的专属战利品配置");
        } else {
            lootConfig.setPerLevelLoot(new HashMap<>());
            logger.fine("§eℹ 未配置各难度专属战利品");
        }
    }
    
    public LootConfig getLootConfig() { return lootConfig; }
    
    // ==================== ✅ 新增配置加载方法（根据 untitled-3.md）====================
    
    /**
     * ✅ 加载波次配置
     */
    private void loadWaveConfig() {
        // ✅ 从 raid/config.yml 加载波次配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection waveSection = raidConfig.getConfigurationSection("wave-settings");
        if (waveSection == null) {
            logger.warning("未找到波次配置，使用默认值");
            this.waveConfig = new WaveConfig();
            this.waveConfig.setWaveOffset(5);
            this.waveConfig.setEasterEggWaves(11);
            this.waveConfig.setWaveDelay(200);
            this.waveConfig.setMobInterval(40);
            return;
        }
        
        this.waveConfig = new WaveConfig();
        waveConfig.setWaveOffset(waveSection.getInt("wave-offset", 5));
        waveConfig.setEasterEggWaves(waveSection.getInt("easter-egg-waves", 11));
        waveConfig.setWaveDelay(waveSection.getLong("wave-delay", 200));
        waveConfig.setMobInterval(waveSection.getLong("mob-interval", 40));
    }
    
    public WaveConfig getWaveConfig() { return waveConfig; }
    
    /**
     * ✅ 加载精英配置
     */
    private void loadEliteConfig() {
        // ✅ 从 raid/config.yml 加载精英配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection eliteSection = raidConfig.getConfigurationSection("elite-settings");
        if (eliteSection == null) {
            logger.warning("未找到精英配置，使用默认值");
            this.eliteConfig = new EliteConfig();
            this.eliteConfig.setHealthMultiplier(1.5);
            this.eliteConfig.setDamageMultiplier(1.3);
            this.eliteConfig.setScaleMultiplier(1.8);
            return;
        }
        
        this.eliteConfig = new EliteConfig();
        
        // 加载各等级概率
        ConfigurationSection chancesSection = eliteSection.getConfigurationSection("chances");
        if (chancesSection != null) {
            Map<Integer, Double> chances = new HashMap<>();
            for (String key : chancesSection.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key.replace("level-", ""));
                    chances.put(level, chancesSection.getDouble(key, 0.0));
                } catch (NumberFormatException e) {
                    // 忽略无效键
                }
            }
            eliteConfig.setChances(chances);
        }
        
        // 加载加成倍数
        ConfigurationSection bonusesSection = eliteSection.getConfigurationSection("bonuses");
        if (bonusesSection != null) {
            eliteConfig.setHealthMultiplier(bonusesSection.getDouble("health-multiplier", 1.5));
            eliteConfig.setDamageMultiplier(bonusesSection.getDouble("damage-multiplier", 1.3));
            eliteConfig.setScaleMultiplier(bonusesSection.getDouble("scale-multiplier", 1.8));
        }
    }
    
    public EliteConfig getEliteConfig() { return eliteConfig; }
    
    /**
     * ✅ 加载灾厄等级配置
     */
    private void loadDoomConfig() {
        // ✅ 从 raid/config.yml 加载灾厄等级配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection doomSection = raidConfig.getConfigurationSection("doom-level-settings");
        if (doomSection == null) {
            logger.warning("未找到灾厄等级配置，使用默认值");
            this.doomConfig = new DoomConfig();
            this.doomConfig.setBonusPerLevel(0.2);
            return;
        }
        
        this.doomConfig = new DoomConfig();
        doomConfig.setBonusPerLevel(doomSection.getDouble("bonus-per-level", 0.2));
    }
    
    public DoomConfig getDoomConfig() { return doomConfig; }
    
    // ✅ 战斗Buff配置
    private CombatBuffConfig combatBuffConfig;
    
    /**
     * ✅ 加载战斗Buff配置
     */
    private void loadCombatBuffConfig() {
        // ✅ 从 raid/config.yml 加载战斗Buff配置
        FileConfiguration raidConfig = loadRaidConfigFile();
        ConfigurationSection combatBuffSection = raidConfig.getConfigurationSection("combat-buff");
        if (combatBuffSection == null) {
            logger.warning("未找到战斗Buff配置，使用默认值");
            this.combatBuffConfig = new CombatBuffConfig();
            this.combatBuffConfig.setEnabled(true);
            this.combatBuffConfig.setRange(32);
            this.combatBuffConfig.setDuration(10);
            this.combatBuffConfig.setInterval(200);
            return;
        }
        
        this.combatBuffConfig = new CombatBuffConfig();
        combatBuffConfig.setEnabled(combatBuffSection.getBoolean("enabled", true));
        combatBuffConfig.setRange(combatBuffSection.getInt("range", 32));
        combatBuffConfig.setDuration(combatBuffSection.getInt("duration", 10));
        combatBuffConfig.setInterval(combatBuffSection.getLong("interval", 200));
    }
    
    public CombatBuffConfig getCombatBuffConfig() { return combatBuffConfig; }
    
    /**
     * ✅ 加载信标守护者配置
     */
    private void loadDefenderConfig() {
        ConfigurationSection defenderSection = config.getConfigurationSection("beacon-defender");
        if (defenderSection == null) {
            logger.warning("未找到信标守护者配置，使用默认值");
            this.beaconDefenderConfig = new BeaconDefenderConfig();
            this.beaconDefenderConfig.setScaleMultiplier(2.0);
            return;
        }
        
        this.beaconDefenderConfig = new BeaconDefenderConfig();
        beaconDefenderConfig.setScaleMultiplier(defenderSection.getDouble("scale-multiplier", 2.0));
    }

    public BeaconDefenderConfig getBeaconDefenderConfig() { return beaconDefenderConfig; }
    
    /**
     * ✅ 加载调试配置
     */
    private void loadDebugConfig() {
        ConfigurationSection debugSection = config.getConfigurationSection("debug");
        if (debugSection == null) {
            logger.warning("未找到调试配置，使用默认值");
            this.debugConfig = new DebugConfig();
            this.debugConfig.setEnabled(false);
            this.debugConfig.setCreeperDetection(true);
            this.debugConfig.setSpawnLocation(false);
            return;
        }
        
        this.debugConfig = new DebugConfig();
        debugConfig.setEnabled(debugSection.getBoolean("enabled", false));
        debugConfig.setCreeperDetection(debugSection.getBoolean("creeper-detection", true));
        debugConfig.setSpawnLocation(debugSection.getBoolean("spawn-location", false));
    }
    
    public DebugConfig getDebugConfig() { return debugConfig; }
    
    // ==================== 内部配置类 ====================
    
    /**
     * 数据库配置
     */
    public static class DatabaseConfig {
        private String type;
        private String mysqlHost;
        private int mysqlPort;
        private String mysqlDatabase;
        private String mysqlUsername;
        private String mysqlPassword;
        private boolean mysqlUseSsl;
        private int poolSize;
        
        // Getters and Setters
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        
        public String getMysqlHost() { return mysqlHost; }
        public void setMysqlHost(String mysqlHost) { this.mysqlHost = mysqlHost; }
        
        public int getMysqlPort() { return mysqlPort; }
        public void setMysqlPort(int mysqlPort) { this.mysqlPort = mysqlPort; }
        
        public String getMysqlDatabase() { return mysqlDatabase; }
        public void setMysqlDatabase(String mysqlDatabase) { this.mysqlDatabase = mysqlDatabase; }
        
        public String getMysqlUsername() { return mysqlUsername; }
        public void setMysqlUsername(String mysqlUsername) { this.mysqlUsername = mysqlUsername; }
        
        public String getMysqlPassword() { return mysqlPassword; }
        public void setMysqlPassword(String mysqlPassword) { this.mysqlPassword = mysqlPassword; }
        
        public boolean isMysqlUseSsl() { return mysqlUseSsl; }
        public void setMysqlUseSsl(boolean mysqlUseSsl) { this.mysqlUseSsl = mysqlUseSsl; }
        
        public int getPoolSize() { return poolSize; }
        public void setPoolSize(int poolSize) { this.poolSize = poolSize; }
    }
    
    /**
     * 袭击系统配置
     */
    public static class RaidConfig {
        private boolean enabled;
        private long cooldown;
        private int detectionRadius;
        private boolean doomEffectEnabled;
        private int villageRadius;  // ✅ 新增：村庄半径
        private int heroEffectDuration;  // ✅ 新增：英雄效果持续时间
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public long getCooldown() { return cooldown; }
        public void setCooldown(long cooldown) { this.cooldown = cooldown; }
        
        public int getDetectionRadius() { return detectionRadius; }
        public void setDetectionRadius(int detectionRadius) { this.detectionRadius = detectionRadius; }
        
        public boolean isDoomEffectEnabled() { return doomEffectEnabled; }
        public void setDoomEffectEnabled(boolean doomEffectEnabled) { this.doomEffectEnabled = doomEffectEnabled; }
        
        public int getVillageRadius() { return villageRadius; }
        public void setVillageRadius(int villageRadius) { this.villageRadius = villageRadius; }
        
        public int getHeroEffectDuration() { return heroEffectDuration; }
        public void setHeroEffectDuration(int heroEffectDuration) { this.heroEffectDuration = heroEffectDuration; }
    }
    
    /**
     * 强化系统配置
     */
    public static class EnhancementConfig {
        private int maxEnhanceCount;
        private List<String> blacklist;
        private Map<String, Integer> limits;
        // ✅ 新增：灾厄之种概率配置 (seedKey -> (强化次数 -> 概率))
        private Map<String, Map<Integer, Double>> seedChances;
        // ✅ 新增：灾厄之种名称映射 (seedKey -> 显示名称)，用于按名称匹配
        private Map<String, String> seedNames;
        // ✅ 新增：附魔数量配置 (professionType -> (heroLevel -> count))
        private Map<String, Map<Integer, Integer>> enchantCounts;
        // ✅ 新增：灾厄之种成书配置 (seedKey -> SeedBookConfig)
        private Map<String, SeedBookConfig> seedBooks;
        
        // Getters and Setters
        public int getMaxEnhanceCount() { return maxEnhanceCount; }
        public void setMaxEnhanceCount(int maxEnhanceCount) { this.maxEnhanceCount = maxEnhanceCount; }
        
        public List<String> getBlacklist() { return blacklist; }
        public void setBlacklist(List<String> blacklist) { this.blacklist = blacklist; }
        
        public Map<String, Integer> getLimits() { return limits; }
        public void setLimits(Map<String, Integer> limits) { this.limits = limits; }
        
        // ✅ 新增：种子概率配置的 Getter/Setter
        public Map<String, Map<Integer, Double>> getSeedChances() { return seedChances; }
        public void setSeedChances(Map<String, Map<Integer, Double>> seedChances) { this.seedChances = seedChances; }
        
        // ✅ 新增：种子名称映射的 Getter/Setter
        public Map<String, String> getSeedNames() { return seedNames; }
        public void setSeedNames(Map<String, String> seedNames) { this.seedNames = seedNames; }
        
        // ✅ 新增：附魔数量配置的 Getter/Setter
        public Map<String, Map<Integer, Integer>> getEnchantCounts() { return enchantCounts; }
        public void setEnchantCounts(Map<String, Map<Integer, Integer>> enchantCounts) { this.enchantCounts = enchantCounts; }
        
        // ✅ 新增：成书配置的 Getter/Setter
        public Map<String, SeedBookConfig> getSeedBooks() { return seedBooks; }
        public void setSeedBooks(Map<String, SeedBookConfig> seedBooks) { this.seedBooks = seedBooks; }
    }
    
    /**
     * ✅ 灾厄之种成书配置
     */
    public static class SeedBookConfig {
        private String title;
        private String author;
        private List<String> pages;
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        
        public List<String> getPages() { return pages; }
        public void setPages(List<String> pages) { this.pages = pages; }
    }
    
    /**
     * 消息配置
     */
    public static class MessageConfig {
        private boolean prefixEnabled;
        private String prefix;
        private boolean soundEnabled;
        private boolean actionbarEnabled;
        private boolean titleEnabled;
        
        // Getters and Setters
        public boolean isPrefixEnabled() { return prefixEnabled; }
        public void setPrefixEnabled(boolean prefixEnabled) { this.prefixEnabled = prefixEnabled; }
        
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        
        public boolean isSoundEnabled() { return soundEnabled; }
        public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
        
        public boolean isActionbarEnabled() { return actionbarEnabled; }
        public void setActionbarEnabled(boolean actionbarEnabled) { this.actionbarEnabled = actionbarEnabled; }
        
        public boolean isTitleEnabled() { return titleEnabled; }
        public void setTitleEnabled(boolean titleEnabled) { this.titleEnabled = titleEnabled; }
    }
    
    /**
     * 性能配置
     */
    public static class PerformanceConfig {
        private boolean asyncTasks;
        private int entitySpawnLimit;
        private int particleLimit;
        private boolean cacheEnabled;
        private long cacheExpiry;
        
        // Getters and Setters
        public boolean isAsyncTasks() { return asyncTasks; }
        public void setAsyncTasks(boolean asyncTasks) { this.asyncTasks = asyncTasks; }
        
        public int getEntitySpawnLimit() { return entitySpawnLimit; }
        public void setEntitySpawnLimit(int entitySpawnLimit) { this.entitySpawnLimit = entitySpawnLimit; }
        
        public int getParticleLimit() { return particleLimit; }
        public void setParticleLimit(int particleLimit) { this.particleLimit = particleLimit; }
        
        public boolean isCacheEnabled() { return cacheEnabled; }
        public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
        
        public long getCacheExpiry() { return cacheExpiry; }
        public void setCacheExpiry(long cacheExpiry) { this.cacheExpiry = cacheExpiry; }
    }
    
    /**
     * Folia配置
     */
    public static class FoliaConfig {
        private boolean enabled;
        private boolean regionScheduler;
        private boolean entityThreadGroup;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public boolean isRegionScheduler() { return regionScheduler; }
        public void setRegionScheduler(boolean regionScheduler) { this.regionScheduler = regionScheduler; }
        
        public boolean isEntityThreadGroup() { return entityThreadGroup; }
        public void setEntityThreadGroup(boolean entityThreadGroup) { this.entityThreadGroup = entityThreadGroup; }
    }
    
    /**
     * 信标配置
     */
    public static class BeaconConfig {
        private boolean enabled;
        private int maxRange;
        private int doomEffectDuration;
        private boolean requireContainer;
        private List<String> containerTypes;
        private Map<Integer, LayerConfig> layers;
        private Map<Integer, Integer> doomLevels;
        private ActivationConfig activationConfig;
        private ActivationConfig easterEggActivationConfig;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getMaxRange() { return maxRange; }
        public void setMaxRange(int maxRange) { this.maxRange = maxRange; }
        
        public int getDoomEffectDuration() { return doomEffectDuration; }
        public void setDoomEffectDuration(int doomEffectDuration) { this.doomEffectDuration = doomEffectDuration; }
        
        public boolean isRequireContainer() { return requireContainer; }
        public void setRequireContainer(boolean requireContainer) { this.requireContainer = requireContainer; }
        
        public List<String> getContainerTypes() { return containerTypes; }
        public void setContainerTypes(List<String> containerTypes) { this.containerTypes = containerTypes; }
        
        public Map<Integer, LayerConfig> getLayers() { return layers; }
        public void setLayers(Map<Integer, LayerConfig> layers) { this.layers = layers; }
        
        public Map<Integer, Integer> getDoomLevels() { return doomLevels; }
        public void setDoomLevels(Map<Integer, Integer> doomLevels) { this.doomLevels = doomLevels; }
        
        public ActivationConfig getActivationConfig() { return activationConfig; }
        public void setActivationConfig(ActivationConfig activationConfig) { this.activationConfig = activationConfig; }
        
        public ActivationConfig getEasterEggActivationConfig() { return easterEggActivationConfig; }
        public void setEasterEggActivationConfig(ActivationConfig easterEggActivationConfig) { this.easterEggActivationConfig = easterEggActivationConfig; }
    }
    
    /**
     * 信标层级配置
     */
    public static class LayerConfig {
        private String material;
        private int size;
        private int offset;
        
        // Getters and Setters
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
        
        public int getOffset() { return offset; }
        public void setOffset(int offset) { this.offset = offset; }
    }
    
    /**
     * 激活配置
     */
    public static class ActivationConfig {
        private String material;
        private int amount;
        private String displayName;
        
        // Getters and Setters
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
    
    /**
     * ✅ 实体配置（新增）
     */
    public static class EntityConfig {
        private double healthMultiplier;
        private double damageMultiplier;
        private double speedMultiplier;
        private int followRange;
        private VillagerGiftConfig villagerGift;
        private DefenderConfig villageDefender;
        private Map<String, MobTargetConfig> mobTargets;
        private EquipmentConfig equipment;
        
        // Getters and Setters
        public double getHealthMultiplier() { return healthMultiplier; }
        public void setHealthMultiplier(double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
        
        public double getDamageMultiplier() { return damageMultiplier; }
        public void setDamageMultiplier(double damageMultiplier) { this.damageMultiplier = damageMultiplier; }
        
        public double getSpeedMultiplier() { return speedMultiplier; }
        public void setSpeedMultiplier(double speedMultiplier) { this.speedMultiplier = speedMultiplier; }
        
        public int getFollowRange() { return followRange; }
        public void setFollowRange(int followRange) { this.followRange = followRange; }
        
        public VillagerGiftConfig getVillagerGift() { return villagerGift; }
        public void setVillagerGift(VillagerGiftConfig villagerGift) { this.villagerGift = villagerGift; }
        
        public DefenderConfig getVillageDefender() { return villageDefender; }
        public void setVillageDefender(DefenderConfig villageDefender) { this.villageDefender = villageDefender; }
        
        public Map<String, MobTargetConfig> getMobTargets() { return mobTargets; }
        public void setMobTargets(Map<String, MobTargetConfig> mobTargets) { this.mobTargets = mobTargets; }
        
        public EquipmentConfig getEquipment() { return equipment; }
        public void setEquipment(EquipmentConfig equipment) { this.equipment = equipment; }
    }
    
    /**
     * ✅ 村民赠礼配置（新增）
     */
    public static class VillagerGiftConfig {
        private double range;
        private int minCooldown;
        private int maxCooldown;
        private long detectionInterval;
        
        // Getters and Setters
        public double getRange() { return range; }
        public void setRange(double range) { this.range = range; }
        
        public int getMinCooldown() { return minCooldown; }
        public void setMinCooldown(int minCooldown) { this.minCooldown = minCooldown; }
        
        public int getMaxCooldown() { return maxCooldown; }
        public void setMaxCooldown(int maxCooldown) { this.maxCooldown = maxCooldown; }
        
        public long getDetectionInterval() { return detectionInterval; }
        public void setDetectionInterval(long detectionInterval) { this.detectionInterval = detectionInterval; }
    }
    
    /**
     * ✅ 村庄守护者配置（新增）
     */
    public static class DefenderConfig {
        private double healthMultiplier;
        private double damageMultiplier;
        private double speedMultiplier;
        
        // Getters and Setters
        public double getHealthMultiplier() { return healthMultiplier; }
        public void setHealthMultiplier(double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
        
        public double getDamageMultiplier() { return damageMultiplier; }
        public void setDamageMultiplier(double damageMultiplier) { this.damageMultiplier = damageMultiplier; }
        
        public double getSpeedMultiplier() { return speedMultiplier; }
        public void setSpeedMultiplier(double speedMultiplier) { this.speedMultiplier = speedMultiplier; }
    }
    
    /**
     * ✅ 战斗Buff配置（新增）
     */
    public static class CombatBuffConfig {
        private boolean enabled;
        private int range;
        private int duration;
        private long interval;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public int getRange() { return range; }
        public void setRange(int range) { this.range = range; }
        
        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }
        
        public long getInterval() { return interval; }
        public void setInterval(long interval) { this.interval = interval; }
    }
    
    /**
     * ✅ 怪物目标配置（新增）
     */
    public static class MobTargetConfig {
        private boolean villagerPriority;
        private int priority;
        
        // Getters and Setters
        public boolean isVillagerPriority() { return villagerPriority; }
        public void setVillagerPriority(boolean villagerPriority) { this.villagerPriority = villagerPriority; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }
    
    /**
     * ✅ 装备配置（新增）
     */
    public static class EquipmentConfig {
        private boolean enabled;
        private Map<String, Double> armorChance;
        private Map<String, Double> weaponChance;
        private Map<String, Integer> enchantmentLevels;
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public Map<String, Double> getArmorChance() { return armorChance; }
        public void setArmorChance(Map<String, Double> armorChance) { this.armorChance = armorChance; }
        
        public Map<String, Double> getWeaponChance() { return weaponChance; }
        public void setWeaponChance(Map<String, Double> weaponChance) { this.weaponChance = weaponChance; }
        
        public Map<String, Integer> getEnchantmentLevels() { return enchantmentLevels; }
        public void setEnchantmentLevels(Map<String, Integer> enchantmentLevels) { this.enchantmentLevels = enchantmentLevels; }
    }
    
    /**
     * ✅ 袭击性能配置（新增）
     */
    public static class RaidPerformanceConfig {
        private long villagerCheckInterval;
        private long bossBarUpdateInterval;
        private long fogEffectInterval;
        private int fogParticleCount;
        private long buffApplyInterval;
        private long raidSchedulerInterval;
        private int maxPlayerCheckCount;
        private Map<String, Double> entityFollowRange;
        private TitleTimingConfig titleTiming;
        
        // Getters and Setters
        public long getVillagerCheckInterval() { return villagerCheckInterval; }
        public void setVillagerCheckInterval(long villagerCheckInterval) { this.villagerCheckInterval = villagerCheckInterval; }
        
        public long getBossBarUpdateInterval() { return bossBarUpdateInterval; }
        public void setBossBarUpdateInterval(long bossBarUpdateInterval) { this.bossBarUpdateInterval = bossBarUpdateInterval; }
        
        public long getFogEffectInterval() { return fogEffectInterval; }
        public void setFogEffectInterval(long fogEffectInterval) { this.fogEffectInterval = fogEffectInterval; }
        
        public int getFogParticleCount() { return fogParticleCount; }
        public void setFogParticleCount(int fogParticleCount) { this.fogParticleCount = fogParticleCount; }
        
        public long getBuffApplyInterval() { return buffApplyInterval; }
        public void setBuffApplyInterval(long buffApplyInterval) { this.buffApplyInterval = buffApplyInterval; }
        
        public long getRaidSchedulerInterval() { return raidSchedulerInterval; }
        public void setRaidSchedulerInterval(long raidSchedulerInterval) {
            // ✅ Folia 要求 runAtFixedRate/runDelayed 初始延迟 >= 1L
            this.raidSchedulerInterval = Math.max(1L, raidSchedulerInterval);
        }
        
        public int getMaxPlayerCheckCount() { return maxPlayerCheckCount; }
        public void setMaxPlayerCheckCount(int maxPlayerCheckCount) { this.maxPlayerCheckCount = maxPlayerCheckCount; }
        
        public Map<String, Double> getEntityFollowRange() { return entityFollowRange; }
        public void setEntityFollowRange(Map<String, Double> entityFollowRange) { this.entityFollowRange = entityFollowRange; }
        
        public TitleTimingConfig getTitleTiming() { return titleTiming; }
        public void setTitleTiming(TitleTimingConfig titleTiming) { this.titleTiming = titleTiming; }
    }
    
    /**
     * ✅ Title显示时间配置（新增）
     */
    public static class TitleTimingConfig {
        private int firstFadeIn;
        private int firstDisplay;
        private int firstFadeOut;
        private int secondFadeIn;
        private int secondDisplay;
        private int secondFadeOut;
        private long delayBetween;
        
        // Getters and Setters
        public int getFirstFadeIn() { return firstFadeIn; }
        public void setFirstFadeIn(int firstFadeIn) { this.firstFadeIn = firstFadeIn; }
        
        public int getFirstDisplay() { return firstDisplay; }
        public void setFirstDisplay(int firstDisplay) { this.firstDisplay = firstDisplay; }
        
        public int getFirstFadeOut() { return firstFadeOut; }
        public void setFirstFadeOut(int firstFadeOut) { this.firstFadeOut = firstFadeOut; }
        
        public int getSecondFadeIn() { return secondFadeIn; }
        public void setSecondFadeIn(int secondFadeIn) { this.secondFadeIn = secondFadeIn; }
        
        public int getSecondDisplay() { return secondDisplay; }
        public void setSecondDisplay(int secondDisplay) { this.secondDisplay = secondDisplay; }
        
        public int getSecondFadeOut() { return secondFadeOut; }
        public void setSecondFadeOut(int secondFadeOut) { this.secondFadeOut = secondFadeOut; }
        
        public long getDelayBetween() { return delayBetween; }
        public void setDelayBetween(long delayBetween) { this.delayBetween = delayBetween; }
    }
    
    /**
     * ✅ 战利品配置（新增）
     */
    public static class LootConfig {
        private int emeraldBaseAmount;
        private int expBottleMultiplier;
        private double enchantedGoldenAppleChance;
        private Map<Integer, List<LevelLootEntry>> perLevelLoot;
        
        // Getters and Setters
        public int getEmeraldBaseAmount() { return emeraldBaseAmount; }
        public void setEmeraldBaseAmount(int emeraldBaseAmount) { this.emeraldBaseAmount = emeraldBaseAmount; }
        
        public int getExpBottleMultiplier() { return expBottleMultiplier; }
        public void setExpBottleMultiplier(int expBottleMultiplier) { this.expBottleMultiplier = expBottleMultiplier; }
        
        public double getEnchantedGoldenAppleChance() { return enchantedGoldenAppleChance; }
        public void setEnchantedGoldenAppleChance(double enchantedGoldenAppleChance) { this.enchantedGoldenAppleChance = enchantedGoldenAppleChance; }
        
        public Map<Integer, List<LevelLootEntry>> getPerLevelLoot() { return perLevelLoot; }
        public void setPerLevelLoot(Map<Integer, List<LevelLootEntry>> perLevelLoot) { this.perLevelLoot = perLevelLoot; }
    }
    
    /**
     * ✅ 每个难度的战利品条目（新增）
     */
    public static class LevelLootEntry {
        private String material;
        private int amount;
        private String enchant;
        private int enchantLevel;
        private int seedType;
        
        // Getters and Setters
        public String getMaterial() { return material; }
        public void setMaterial(String material) { this.material = material; }
        
        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }
        
        public String getEnchant() { return enchant; }
        public void setEnchant(String enchant) { this.enchant = enchant; }
        
        public int getEnchantLevel() { return enchantLevel; }
        public void setEnchantLevel(int enchantLevel) { this.enchantLevel = enchantLevel; }
        
        public int getSeedType() { return seedType; }
        public void setSeedType(int seedType) { this.seedType = seedType; }
    }
    
    /**
     * ✅ 波次配置（根据 untitled-3.md 新增）
     */
    public static class WaveConfig {
        private int waveOffset;
        private int easterEggWaves;
        private long waveDelay;
        private long mobInterval;
        
        // Getters and Setters
        public int getWaveOffset() { return waveOffset; }
        public void setWaveOffset(int waveOffset) { this.waveOffset = waveOffset; }
        
        public int getEasterEggWaves() { return easterEggWaves; }
        public void setEasterEggWaves(int easterEggWaves) { this.easterEggWaves = easterEggWaves; }
        
        public long getWaveDelay() { return waveDelay; }
        public void setWaveDelay(long waveDelay) { this.waveDelay = waveDelay; }
        
        public long getMobInterval() { return mobInterval; }
        public void setMobInterval(long mobInterval) { this.mobInterval = mobInterval; }
    }
    
    /**
     * ✅ 精英配置（根据 untitled-3.md 新增）
     */
    public static class EliteConfig {
        private Map<Integer, Double> chances;
        private double healthMultiplier;
        private double damageMultiplier;
        private double scaleMultiplier;
        
        // Getters and Setters
        public Map<Integer, Double> getChances() { return chances; }
        public void setChances(Map<Integer, Double> chances) { this.chances = chances; }
        
        public double getHealthMultiplier() { return healthMultiplier; }
        public void setHealthMultiplier(double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
        
        public double getDamageMultiplier() { return damageMultiplier; }
        public void setDamageMultiplier(double damageMultiplier) { this.damageMultiplier = damageMultiplier; }
        
        public double getScaleMultiplier() { return scaleMultiplier; }
        public void setScaleMultiplier(double scaleMultiplier) { this.scaleMultiplier = scaleMultiplier; }
    }
    
    /**
     * ✅ 灾厄等级配置（根据 untitled-3.md 新增）
     */
    public static class DoomConfig {
        private double bonusPerLevel;
        
        // Getters and Setters
        public double getBonusPerLevel() { return bonusPerLevel; }
        public void setBonusPerLevel(double bonusPerLevel) { this.bonusPerLevel = bonusPerLevel; }
    }
    
    /**
     * ✅ 调试配置（新增）
     */
    public static class DebugConfig {
        private boolean enabled;
        private boolean creeperDetection;
        private boolean spawnLocation;  // ✅ 新增：怪物生成位置调试
        
        // Getters and Setters
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        
        public boolean isCreeperDetection() { return creeperDetection; }
        public void setCreeperDetection(boolean creeperDetection) { this.creeperDetection = creeperDetection; }
        
        public boolean isSpawnLocation() { return spawnLocation; }
        public void setSpawnLocation(boolean spawnLocation) { this.spawnLocation = spawnLocation; }
    }
    
    /**
     * ✅ 信标守护者配置（新增）
     */
    public static class BeaconDefenderConfig {
        private double scaleMultiplier;
        
        public double getScaleMultiplier() { return scaleMultiplier; }
        public void setScaleMultiplier(double scaleMultiplier) { this.scaleMultiplier = scaleMultiplier; }
    }
}