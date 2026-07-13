package org.yinwu;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;
import org.bukkit.Bukkit;
import org.yinwu.beacon.InvertedBeaconDetector;
import org.yinwu.beacon.BeaconInteractionListener;
import org.yinwu.command.YinwuRaidCommand;
import org.yinwu.effect.DoomEffectManager;
import org.yinwu.raid.SpecialRaidListener;
import org.yinwu.reward.VillagerRewardManager;
import org.yinwu.reward.GiftThrowManager;
import org.yinwu.config.ConfigManager;

/**
 * YinwuRaid 插件主类
 * 严格遵守 Folia 的区域线程机制
 * 
 * @author Yinwu
 * @version 1.0.0
 */
public class YinwuRaidPlugin extends JavaPlugin {
    
    private static YinwuRaidPlugin instance;
    
    // 配置管理器
    private ConfigManager configManager;
    
    // 核心组件
    private InvertedBeaconDetector beaconDetector;
    private DoomEffectManager effectManager;
    private SpecialRaidListener specialRaidListener;
    
    // 奖励系统
    private VillagerRewardManager rewardManager;
    private GiftThrowManager giftThrowManager;
    
    // 命令处理器
    private YinwuRaidCommand commandHandler;
    
    @Override
    public void onEnable() {
        instance = this;
        
        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onEnable: 开始启用插件");
        }
        
        // ✅ 保存默认配置文件（包括子目录）
        saveDefaultFiles();
        
        // ✅ 初始化配置管理器（统一配置加载）
        this.configManager = new ConfigManager(this);
        if (configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onEnable: 配置管理器初始化完成");
        }
        
        // 检查插件是否启用
        if (!configManager.isEnabled()) {
            getLogger().warning("§e[YinwuRaid] 插件已禁用，请在配置中启用");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        
        // 初始化核心组件
        initializeComponents();
        if (configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onEnable: 核心组件初始化完成");
        }
        
        // 注册监听器
        registerListeners();
        
        // 注册命令
        registerCommands();
        if (configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onEnable: 命令系统注册完成");
        }
        
        // Folia 兼容的异步初始化
        Bukkit.getGlobalRegionScheduler().runDelayed(this, (task) -> {
            getLogger().info("§6[YinwuRaid] §a插件已启用！");
        }, 1L);
    }
    
    /**
     * 初始化核心组件
     */
    private void initializeComponents() {
        // ✅ 初始化附魔规则管理器（从配置加载）- 改为实例化
        org.yinwu.beacon.EnchantmentRuleManager.initialize(this);
        
        this.beaconDetector = new InvertedBeaconDetector(this);
        this.effectManager = new DoomEffectManager(this);
        this.rewardManager = new VillagerRewardManager(this);
        this.giftThrowManager = new GiftThrowManager(this, rewardManager);
        
        getLogger().info("§6[YinwuRaid] §a核心组件初始化完成");
    }
    
    /**
     * 注册命令
     */
    private void registerCommands() {
        this.commandHandler = new YinwuRaidCommand(this);
        
        // 注册主命令
        getCommand("yinwuraid").setExecutor(commandHandler);
        getCommand("yinwuraid").setTabCompleter(commandHandler);
        
        getLogger().info("§6[YinwuRaid] §a命令系统注册完成");
    }
    
    /**
     * 注册监听器
     */
    private void registerListeners() {
        // 注册信标交互监听器
        BeaconInteractionListener beaconListener = new BeaconInteractionListener(
            this, 
            beaconDetector, 
            effectManager
        );
        getServer().getPluginManager().registerEvents(beaconListener, this);
        if (configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] registerListeners: BeaconInteractionListener 已注册");
        }
            
        // 注册灾厄袭击监听器（防止与原版冲突）
        specialRaidListener = new SpecialRaidListener(this, effectManager);
        getServer().getPluginManager().registerEvents(specialRaidListener, this);
        if (configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] registerListeners: SpecialRaidListener 已注册");
        }
            
        getLogger().info("§6[YinwuRaid] §a 监听器注册完成");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("§6[YinwuRaid] §c正在禁用插件...");
        
        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onDisable: 开始清理资源");
        }
        
        // 清理灾厄袭击相关资源
        if (specialRaidListener != null) {
            specialRaidListener.cleanup();
            if (configManager != null && configManager.isDebugEnabled()) {
                getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onDisable: 袭击监听器已清理");
            }
        }
        
        // 清理灾厄效果相关资源
        if (effectManager != null) {
            effectManager.cleanup();
            if (configManager != null && configManager.isDebugEnabled()) {
                getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onDisable: 效果管理器已清理");
            }
        }
        
        HandlerList.unregisterAll(this);
        getLogger().info("§6[YinwuRaid] §c插件已安全禁用");
    }
    
    /**
     * 获取插件实例（静态访问）
     * 注意：在 Folia 中使用时需要确保在线程安全的环境下调用
     * 
     * @return 插件实例
     */
    public static YinwuRaidPlugin getInstance() {
        if (instance == null) {
            throw new IllegalStateException("插件尚未初始化!");
        }
        return instance;
    }
    
    /**
     * 获取配置管理器
     */
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * 获取信标检测器
     * 
     * @return 信标检测器
     */
    public InvertedBeaconDetector getBeaconDetector() {
        return beaconDetector;
    }
    
    /**
     * 获取灾厄效果管理器
     * 
     * @return 灾厄效果管理器
     */
    public DoomEffectManager getEffectManager() {
        return effectManager;
    }
    
    /**
     * 获取灾厄袭击监听器
     * 
     * @return 灾厄袭击监听器
     */
    public SpecialRaidListener getSpecialRaidListener() {
        return specialRaidListener;
    }
    
    /**
     * 获取村庄英雄奖励管理器
     * 
     * @return 奖励管理器
     */
    public VillagerRewardManager getRewardManager() {
        return rewardManager;
    }
    
    /**
     * ✅ 保存默认配置文件（包括子目录）
     */
    private void saveDefaultFiles() {
        // 保存主配置文件
        saveDefaultConfig();
        
        // 保存 raid/ 目录下的所有文件
        String[] raidFiles = {
            "raid/config.yml",
            "raid/level-6.yml",
            "raid/level-7.yml",
            "raid/level-8.yml",
            "raid/level-9.yml",
            "raid/level-10.yml",
            "raid/mob-names.yml"
        };
        
        for (String file : raidFiles) {
            if (getResource(file) != null) {
                java.io.File outFile = new java.io.File(getDataFolder(), file);
                if (!outFile.exists()) {
                    saveResource(file, false);
                }
            }
        }
        
        // 保存 rewards/ 目录下的所有文件
        String[] rewardFiles = {
            "rewards/config.yml",
            "rewards/default.yml",
            "rewards/armorer.yml",
            "rewards/butcher.yml",
            "rewards/cartographer.yml",
            "rewards/cleric.yml",
            "rewards/farmer.yml",
            "rewards/fisherman.yml",
            "rewards/fletcher.yml",
            "rewards/leatherworker.yml",
            "rewards/librarian.yml",
            "rewards/mason.yml",
            "rewards/nitwit.yml",
            "rewards/shepherd.yml",
            "rewards/toolsmith.yml",
            "rewards/weaponsmith.yml"
        };
        
        for (String file : rewardFiles) {
            if (getResource(file) != null) {
                java.io.File outFile = new java.io.File(getDataFolder(), file);
                if (!outFile.exists()) {
                    saveResource(file, false);
                }
            }
        }
    }
}
