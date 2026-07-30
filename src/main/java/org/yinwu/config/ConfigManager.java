package org.yinwu.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yinwu.YinwuRaidPlugin;

import java.io.File;

/**
 * 配置管理器 — 薄委派层，各配置 POJO 自行加载
 */
public class ConfigManager {

    private final YinwuRaidPlugin plugin;
    private final FileConfiguration config;

    private FileConfiguration cachedRaidConfig;
    private FileConfiguration cachedRewardsConfig;

    // 基础配置
    private boolean enabled;
    private String language;
    private boolean checkUpdates;
    private boolean debug;

    // 配置 POJO
    private DatabaseConfig databaseConfig;
    private RaidConfig raidConfig;
    private BeaconConfig beaconConfig;
    private EnhancementConfig enhancementConfig;
    private MessageConfig messageConfig;
    private PerformanceConfig performanceConfig;
    private FoliaConfig foliaConfig;
    private EntityConfig entityConfig;
    private RaidPerformanceConfig raidPerformanceConfig;
    private LootConfig lootConfig;
    private WaveConfig waveConfig;
    private EliteConfig eliteConfig;
    private DoomConfig doomConfig;
    private CombatBuffConfig combatBuffConfig;
    private BeaconDefenderConfig beaconDefenderConfig;
    private DebugConfig debugConfig;

    public ConfigManager(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        plugin.saveDefaultConfig();
        loadAll();
    }

    /** 热重载所有配置 */
    public void reload() {
        plugin.reloadConfig();
        cachedRaidConfig = null;
        cachedRewardsConfig = null;
        loadAll();
    }

    private void loadAll() {
        // 基础配置
        this.enabled = config.getBoolean("enabled", true);
        this.language = config.getString("language", "zh_CN");
        this.checkUpdates = config.getBoolean("check-updates", true);
        this.debug = config.getBoolean("debug", false);

        // === 主配置 config.yml ===
        this.databaseConfig = DatabaseConfig.from(config.getConfigurationSection("database"));
        this.raidConfig = RaidConfig.from(config.getConfigurationSection("raid"));
        this.messageConfig = MessageConfig.from(config.getConfigurationSection("messages"));
        this.performanceConfig = PerformanceConfig.from(config.getConfigurationSection("performance"));
        this.foliaConfig = FoliaConfig.from(config.getConfigurationSection("folia"));
        this.raidPerformanceConfig = RaidPerformanceConfig.from(config.getConfigurationSection("raid-performance"));
        this.beaconDefenderConfig = BeaconDefenderConfig.from(config.getConfigurationSection("beacon-defender"));
        this.debugConfig = DebugConfig.from(config.getConfigurationSection("debug"));

        // === 袭击配置 raid/config.yml ===
        FileConfiguration rc = loadRaidConfigFile();
        this.beaconConfig = BeaconConfig.from(rc.getConfigurationSection("beacon"));
        this.entityConfig = EntityConfig.from(rc.getConfigurationSection("entities"));
        this.lootConfig = LootConfig.from(rc.getConfigurationSection("raid-loot"));
        this.waveConfig = WaveConfig.from(rc.getConfigurationSection("wave-settings"));
        this.eliteConfig = EliteConfig.from(rc.getConfigurationSection("elite-settings"));
        this.doomConfig = DoomConfig.from(rc.getConfigurationSection("doom-level-settings"));
        this.combatBuffConfig = CombatBuffConfig.from(rc.getConfigurationSection("combat-buff"));

        // === 奖励配置 rewards/config.yml ===
        FileConfiguration rwc = loadRewardsConfigFile();
        this.enhancementConfig = EnhancementConfig.from(rwc.getConfigurationSection("enhancement"));
    }

    // ========== 基础配置 getter ==========

    public boolean isEnabled() { return enabled; }
    public String getLanguage() { return language; }
    public boolean shouldCheckUpdates() { return checkUpdates; }
    public boolean isDebugEnabled() { return debug; }

    // ========== 配置 POJO getter ==========

    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public RaidConfig getRaidConfig() { return raidConfig; }
    public BeaconConfig getBeaconConfig() { return beaconConfig; }
    public EnhancementConfig getEnhancementConfig() { return enhancementConfig; }
    public MessageConfig getMessageConfig() { return messageConfig; }
    public PerformanceConfig getPerformanceConfig() { return performanceConfig; }
    public FoliaConfig getFoliaConfig() { return foliaConfig; }
    public EntityConfig getEntityConfig() { return entityConfig; }
    public RaidPerformanceConfig getRaidPerformanceConfig() { return raidPerformanceConfig; }
    public LootConfig getLootConfig() { return lootConfig; }
    public WaveConfig getWaveConfig() { return waveConfig; }
    public EliteConfig getEliteConfig() { return eliteConfig; }
    public DoomConfig getDoomConfig() { return doomConfig; }
    public CombatBuffConfig getCombatBuffConfig() { return combatBuffConfig; }
    public BeaconDefenderConfig getBeaconDefenderConfig() { return beaconDefenderConfig; }
    public DebugConfig getDebugConfig() { return debugConfig; }

    public FileConfiguration getBukkitConfig() { return config; }

    // ========== 子文件加载（带缓存） ==========

    private FileConfiguration loadRaidConfigFile() {
        if (cachedRaidConfig != null) return cachedRaidConfig;
        File f = new File(plugin.getDataFolder(), "raid/config.yml");
        if (!f.exists()) {
            plugin.getLogger().warning("未找到 raid/config.yml，使用主 config.yml");
            cachedRaidConfig = config;
        } else {
            cachedRaidConfig = YamlConfiguration.loadConfiguration(f);
        }
        return cachedRaidConfig;
    }

    private FileConfiguration loadRewardsConfigFile() {
        if (cachedRewardsConfig != null) return cachedRewardsConfig;
        File f = new File(plugin.getDataFolder(), "rewards/config.yml");
        if (!f.exists()) {
            plugin.getLogger().warning("未找到 rewards/config.yml，使用主 config.yml");
            cachedRewardsConfig = config;
        } else {
            cachedRewardsConfig = YamlConfiguration.loadConfiguration(f);
        }
        return cachedRewardsConfig;
    }
}
