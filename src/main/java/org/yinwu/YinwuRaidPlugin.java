package org.yinwu;

import net.yinwu.lib.api.RaidAPI;
import net.yinwu.lib.plugin.YinwuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.yinwu.api.RaidAPIImpl;
import org.yinwu.beacon.InvertedBeaconDetector;
import org.yinwu.beacon.BeaconInteractionListener;
import org.yinwu.command.YinwuRaidCommand;
import org.yinwu.effect.DoomEffectManager;
import org.yinwu.raid.SpecialRaidListener;
import org.yinwu.reward.VillagerRewardManager;
import org.yinwu.reward.GiftThrowManager;
import org.yinwu.config.ConfigManager;

public class YinwuRaidPlugin extends YinwuPlugin {

    private static YinwuRaidPlugin instance;

    private ConfigManager configManager;
    private InvertedBeaconDetector beaconDetector;
    private DoomEffectManager effectManager;
    private BeaconInteractionListener beaconListener;
    private SpecialRaidListener specialRaidListener;
    private VillagerRewardManager rewardManager;
    private GiftThrowManager giftThrowManager;
    private YinwuRaidCommand commandHandler;

    @Override
    public String name() {
        return "YinwuRaid";
    }

    @Override
    public void enable() {
        instance = this;

        saveDefaultFiles();

        this.configManager = new ConfigManager(this);
        if (configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onEnable: 配置管理器初始化完成");
        }

        if (!configManager.isEnabled()) {
            getLogger().warning("§e[YinwuRaid] 插件已禁用，请在配置中启用");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        initializeComponents();

        // 先初始化监听器（specialRaidListener 需要被创建）
        registerListeners();
        registerCommands();

        // 注册 RaidAPI 服务（供其他 Yinwu 插件调用）
        Bukkit.getServicesManager().register(RaidAPI.class,
            new RaidAPIImpl(this, specialRaidListener),
            this, ServicePriority.Normal);

        // 尝试链接 Forge —— 袭击掉落可锻造材料
        tryForgeLink();
        // 尝试链接 Enchant —— 袭击奖励可含附魔书
        tryEnchantLink();

        Bukkit.getGlobalRegionScheduler().runDelayed(this, (task) -> {
            getLogger().info("§6[YinwuRaid] §a插件已启用！");
        }, 1L);
    }

    private void tryForgeLink() {
        org.yinwu.util.ForgeBridge.init();
        if (org.yinwu.util.ForgeBridge.isAvailable()) {
            getLogger().info("§a✓ 检测到 YinwuForge —— 袭击奖励将包含可锻造材料");
            // 给 loot 系统注入 forge 材料掉落
            if (specialRaidListener != null) {
                specialRaidListener.getLootManager().setForgeLinked(true);
            }
        }
    }

    private void tryEnchantLink() {
        // 各插件各自 shade YinwuPluginLib，ServicesManager 按 Class 匹配会失效；
        // 改用反射桥通过 YinwuEnchant 的 ClassLoader 拿服务实例
        org.yinwu.util.EnchantBridge.init();
        if (org.yinwu.util.EnchantBridge.isAvailable()) {
            getLogger().info("§a✓ 检测到 YinwuEnchant —— 袭击奖励将包含自定义附魔书");
        }
    }

    @Override
    public void disable() {
        getLogger().info("§6[YinwuRaid] §c正在禁用插件...");

        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().info("§e[DEBUG] [YinwuRaidPlugin] onDisable: 开始清理资源");
        }

        if (specialRaidListener != null) {
            specialRaidListener.cleanup();
        }
        if (effectManager != null) {
            effectManager.cleanup();
        }
        if (giftThrowManager != null) {
            giftThrowManager.cleanup();
        }

        // Rule 8：反注册本插件所有监听器，避免重载时重复注册
        HandlerList.unregisterAll(this);

        // 清除静态单例引用，避免 reload 后 getInstance() 返回旧实例
        instance = null;

        getLogger().info("§6[YinwuRaid] §c插件已安全禁用");
    }

    private void initializeComponents() {
        org.yinwu.beacon.EnchantmentRuleManager.initialize(this);

        this.beaconDetector = new InvertedBeaconDetector(this);
        this.effectManager = new DoomEffectManager(this);
        this.rewardManager = new VillagerRewardManager(this);
        this.giftThrowManager = new GiftThrowManager(this, rewardManager);
        getLogger().info("§6[YinwuRaid] §a核心组件初始化完成");
    }

    private void registerCommands() {
        this.commandHandler = new YinwuRaidCommand(this);
        getCommand("yinwuraid").setExecutor(commandHandler);
        getCommand("yinwuraid").setTabCompleter(commandHandler);
        getLogger().info("§6[YinwuRaid] §a命令系统注册完成");
    }

    private void registerListeners() {
        this.beaconListener = new BeaconInteractionListener(this, beaconDetector, effectManager);
        getServer().getPluginManager().registerEvents(beaconListener, this);

        specialRaidListener = new SpecialRaidListener(this, effectManager);
        getServer().getPluginManager().registerEvents(specialRaidListener, this);
        getLogger().info("§6[YinwuRaid] §a 监听器注册完成");
    }

    // ---- getters ----

    public static YinwuRaidPlugin getInstance() {
        if (instance == null) throw new IllegalStateException("插件尚未初始化!");
        return instance;
    }

    public ConfigManager getConfigManager() { return configManager; }
    public InvertedBeaconDetector getBeaconDetector() { return beaconDetector; }
    public DoomEffectManager getEffectManager() { return effectManager; }
    public SpecialRaidListener getSpecialRaidListener() { return specialRaidListener; }
    public VillagerRewardManager getRewardManager() { return rewardManager; }

    private void saveDefaultFiles() {
        saveDefaultConfig();
        for (String file : new String[]{
            "raid/config.yml", "raid/level-6.yml", "raid/level-7.yml",
            "raid/level-8.yml", "raid/level-9.yml", "raid/level-10.yml",
            "raid/mob-names.yml"
        }) {
            if (getResource(file) != null) {
                java.io.File outFile = new java.io.File(getDataFolder(), file);
                if (!outFile.exists()) saveResource(file, false);
            }
        }
        for (String file : new String[]{
            "rewards/config.yml", "rewards/default.yml",
            "rewards/armorer.yml", "rewards/butcher.yml", "rewards/cartographer.yml",
            "rewards/cleric.yml", "rewards/farmer.yml", "rewards/fisherman.yml",
            "rewards/fletcher.yml", "rewards/leatherworker.yml", "rewards/librarian.yml",
            "rewards/mason.yml", "rewards/nitwit.yml", "rewards/shepherd.yml",
            "rewards/toolsmith.yml", "rewards/weaponsmith.yml"
        }) {
            if (getResource(file) != null) {
                java.io.File outFile = new java.io.File(getDataFolder(), file);
                if (!outFile.exists()) saveResource(file, false);
            }
        }
    }
}
