package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.event.raid.RaidTriggerEvent;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.effect.DoomEffectManager;
import org.yinwu.util.MythicMobsIntegration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灾厄袭击监听器
 * 处理基于高等级灾厄效果的灾厄袭击事件
 */
public class SpecialRaidListener implements Listener {

    private final YinwuRaidPlugin plugin;
    private final DoomEffectManager effectManager;
    private final ConfigManager configManager;
    private final MythicMobsIntegration mythicMobsIntegration;

    // ✅ 管理器实例
    private final RaidMobManager mobManager;
    private final RaidBossBarManager bossBarManager;
    private final RaidFogEffectManager fogEffectManager;
    private final RaidDefenderManager defenderManager;
    private final RaidLootManager lootManager;

    // 存储每个世界的灾厄信标位置（世界名称 -> 信标方块位置）
    private final Map<String, Location> beaconLocations = new ConcurrentHashMap<>();

    // 存储每个灾厄信标的袭击状态（信标位置 -> 袭击状态）
    private final Map<Location, RaidState> raidStatesByBeacon = new ConcurrentHashMap<>();

    // 兼容旧代码：玩家 UUID -> RaidState
    private final Map<UUID, RaidState> raidStates = new ConcurrentHashMap<>();

    // ✅ 配置字段
    private int waveOffset;
    private int easterEggWaves;
    private final Map<Integer, Integer> wavesPerLevel = new HashMap<>();
    private long waveDelay = 200;
    private long mobInterval = 40;
    private double doomLevelBonus = 0.2;
    private double defenderScaleMultiplier = 2.0;

    // ✅ 性能配置
    private long villagerCheckInterval;
    private long bossBarUpdateInterval;
    private long fogEffectInterval;
    private int fogParticleCount;
    private long buffApplyInterval;
    private long raidSchedulerInterval;
    private int maxPlayerCheckCount = 50;

    // ✅ 缓存的配置值
    private double cachedHealthMultiplier;
    private double cachedDamageMultiplier;
    private double cachedSpeedMultiplier;
    private int cachedFollowRange;
    private int cachedVillageRadius;

    // ✅ 实体侦测范围配置
    private double beaconDefenderFollowRange = 64.0;
    private double giantGolemFollowRange = 48.0;
    private double normalGolemFollowRange = 64.0;
    private double otherMobFollowRange = 48.0;

    // ✅ Title显示时间配置
    private int titleFailFirstFadeIn = 10;
    private int titleFailFirstDisplay = 60;
    private int titleFailFirstFadeOut = 20;
    private int titleFailSecondFadeIn = 10;
    private int titleFailSecondDisplay = 100;
    private int titleFailSecondFadeOut = 20;
    private long titleFailDelay = 80;

    // ✅ 灾厄效果检测任务
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask doomDetectionTask;
    
    // ✅ 快速村民死亡检测任务（每20tick检查一次，平衡性能与即时性）
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask villagerDeathCheckTask;

    // ✅ 预搜索任务管理（玩家 UUID -> ScheduledTask）
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> preSearchTasks = new ConcurrentHashMap<>();

    // ✅ 性能监控：统计信息
    private long pluginStartTime = System.currentTimeMillis();
    private int totalRaidsTriggered = 0;
    private int totalRaidsCompleted = 0;
    private int totalRaidsFailed = 0;
    private int totalMobsSpawned = 0;
    private int totalBossBarsCreated = 0;

    // ✅ 调试配置
    private boolean debugEnabled = false;
    private boolean creeperDetectionDebug = true;
    private boolean spawnLocationDebug = false;

    // ✅ 魔法数字常量
    private static final int EASTER_EGG_LEVEL = 6;
    private static final int MIN_DOOM_LEVEL = 7;
    private static final int MAX_NORMAL_DOOM_LEVEL = 10;
    private static final int MIN_WORLD_HEIGHT = -64;
    private static final int MAX_WORLD_HEIGHT = 320;
    private static final long DEFAULT_WAVE_DELAY = 200L;
    private static final long DEFAULT_MOB_INTERVAL = 40L;

    public SpecialRaidListener(YinwuRaidPlugin plugin, DoomEffectManager effectManager) {
        this.plugin = plugin;
        this.effectManager = effectManager;
        this.configManager = plugin.getConfigManager();
        this.mythicMobsIntegration = new MythicMobsIntegration(plugin);

        // 初始化管理器
        this.mobManager = new RaidMobManager(plugin, configManager, mythicMobsIntegration, this);
        this.bossBarManager = new RaidBossBarManager(plugin, mobManager);
        this.fogEffectManager = new RaidFogEffectManager(plugin, configManager);
        this.defenderManager = new RaidDefenderManager(plugin, configManager, this, mobManager);
        this.lootManager = new RaidLootManager(plugin, configManager, this);

        // 使用 ConfigManager 获取实体配置
        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        this.cachedHealthMultiplier = entityConfig.getHealthMultiplier();
        this.cachedDamageMultiplier = entityConfig.getDamageMultiplier();
        this.cachedSpeedMultiplier = entityConfig.getSpeedMultiplier();
        this.cachedFollowRange = entityConfig.getFollowRange();

        ConfigManager.RaidConfig raidConfig = configManager.getRaidConfig();
        this.cachedVillageRadius = raidConfig != null ? raidConfig.getVillageRadius() : 48;

        loadDebugConfig();
        loadPerformanceConfig();
        loadRaidMobsConfig();
        loadBeaconLocations();

        // 注意：周期性任务现在在管理器构造函数中启动
        // 灾厄效果检测任务改为信标激活后启动
    }

    // ==================== 配置加载 ====================

    private void loadDebugConfig() {
        debugEnabled = configManager.isDebugEnabled();
        creeperDetectionDebug = true;
        ConfigManager.DebugConfig debugConfig = configManager.getDebugConfig();
        if (debugConfig != null) {
            spawnLocationDebug = debugConfig.isSpawnLocation();
        } else {
            spawnLocationDebug = false;
        }

        if (debugEnabled) {
            plugin.getLogger().info("§e\u2734 调试模式已启用！将输出详细日志。");
        }
        if (creeperDetectionDebug) {
            plugin.getLogger().info("§e\u2734 苦力怕自爆检测日志已启用。");
        }
        if (spawnLocationDebug) {
            plugin.getLogger().info("§e\u2734 怪物生成位置调试日志已启用。");
        }

        mobManager.setSpawnLocationDebug(spawnLocationDebug);
        mobManager.setCreeperDetectionDebug(creeperDetectionDebug);
    }

    private void loadPerformanceConfig() {
        ConfigManager.RaidPerformanceConfig perfConfig = configManager.getRaidPerformanceConfig();
        if (perfConfig != null) {
            villagerCheckInterval = perfConfig.getVillagerCheckInterval();
            bossBarUpdateInterval = perfConfig.getBossBarUpdateInterval();
            fogEffectInterval = perfConfig.getFogEffectInterval();
            fogParticleCount = perfConfig.getFogParticleCount();
            buffApplyInterval = perfConfig.getBuffApplyInterval();
            raidSchedulerInterval = perfConfig.getRaidSchedulerInterval();
            maxPlayerCheckCount = perfConfig.getMaxPlayerCheckCount();

            Map<String, Double> followRanges = perfConfig.getEntityFollowRange();
            if (followRanges != null) {
                beaconDefenderFollowRange = followRanges.getOrDefault("beacon-defender", 64.0);
                giantGolemFollowRange = followRanges.getOrDefault("giant-golem", 48.0);
                normalGolemFollowRange = followRanges.getOrDefault("normal-golem", 64.0);
                otherMobFollowRange = followRanges.getOrDefault("other-mobs", 48.0);
            }

            ConfigManager.TitleTimingConfig titleConfig = perfConfig.getTitleTiming();
            if (titleConfig != null) {
                titleFailFirstFadeIn = titleConfig.getFirstFadeIn();
                titleFailFirstDisplay = titleConfig.getFirstDisplay();
                titleFailFirstFadeOut = titleConfig.getFirstFadeOut();
                titleFailSecondFadeIn = titleConfig.getSecondFadeIn();
                titleFailSecondDisplay = titleConfig.getSecondDisplay();
                titleFailSecondFadeOut = titleConfig.getSecondFadeOut();
                titleFailDelay = titleConfig.getDelayBetween();
            }

            plugin.getLogger().fine(String.format("§a\u2713 性能配置：村民检测=%dtick, BossBar更新=%dtick, 迷雾=%dtick/%d个, Buff=%dtick, 调度器=%dtick, 玩家检测上限=%d",
                villagerCheckInterval, bossBarUpdateInterval, fogEffectInterval, fogParticleCount, buffApplyInterval, raidSchedulerInterval, maxPlayerCheckCount));
        } else {
            villagerCheckInterval = 60;
            bossBarUpdateInterval = 20;
            fogEffectInterval = 10;
            fogParticleCount = 30;
            buffApplyInterval = 200;
            raidSchedulerInterval = 60;
            maxPlayerCheckCount = 50;
            plugin.getLogger().fine("§e\u26A0 未找到 raid-performance 配置，使用默认值");
        }

        loadLootConfig();
    }

    private void loadLootConfig() {
        // 战利品配置由 RaidLootManager 管理
        lootManager.reloadLootConfig();

        ConfigManager.LootConfig lootConfig = configManager.getLootConfig();
        if (lootConfig != null) {
            plugin.getLogger().fine(String.format("§a\u2713 战利品配置：绿宝石基础=%d, 经验瓶倍数=%d, 附魔金苹果概率=%.1f%%",
                lootConfig.getEmeraldBaseAmount(), lootConfig.getExpBottleMultiplier(), lootConfig.getEnchantedGoldenAppleChance() * 100));
        } else {
            plugin.getLogger().fine("§e\u26A0 未找到 raid-loot 配置，使用默认值");
        }
    }

    // ==================== 清理 ====================

    public void cleanup() {
        plugin.getLogger().info("§6[YinwuRaid] §e正在清理灾厄袭击资源...");

        // 1. 清理所有 BossBar（委托给管理器）
        bossBarManager.cleanup();

        // 2. 取消所有定时任务（委托给管理器）
        defenderManager.cleanup();

        // 3. 取消灾厄效果检测任务
        if (doomDetectionTask != null && !doomDetectionTask.isCancelled()) {
            doomDetectionTask.cancel();
        }

        // ✅ 取消快速村民死亡检测任务
        if (villagerDeathCheckTask != null && !villagerDeathCheckTask.isCancelled()) {
            villagerDeathCheckTask.cancel();
            villagerDeathCheckTask = null;
        }

        // 4. 清理迷雾效果
        fogEffectManager.cleanup();

        // 5. 清理怪物资源（委托给管理器）
        mobManager.cleanup();

        // 6. 清理预搜索任务
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : preSearchTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        preSearchTasks.clear();

        // 7. 清理袭击状态
        raidStates.clear();
        raidStatesByBeacon.clear();

        // 8. 打印性能统计
        printPerformanceStats();
    }

    public void reload() {
        plugin.getLogger().info("§6[YinwuRaid] §e正在重新加载灾厄袭击配置...");

        // 重新加载
        mobManager.reload();
        loadDebugConfig();
        loadRaidMobsConfig();
        loadPerformanceConfig();
        lootManager.reloadLootConfig();

        ConfigManager.EntityConfig entityConfig = configManager.getEntityConfig();
        this.cachedHealthMultiplier = entityConfig.getHealthMultiplier();
        this.cachedDamageMultiplier = entityConfig.getDamageMultiplier();
        this.cachedSpeedMultiplier = entityConfig.getSpeedMultiplier();
        this.cachedFollowRange = entityConfig.getFollowRange();

        ConfigManager.RaidConfig raidConfig = configManager.getRaidConfig();
        this.cachedVillageRadius = raidConfig != null ? raidConfig.getVillageRadius() : 48;

        plugin.getLogger().info("§a\u2713 灾厄袭击配置重新加载完成");
        plugin.getLogger().info(String.format("§a\u2713 MythicMobs 状态：%s",
            mythicMobsIntegration.isAvailable() ? "已启用" : "未启用"));
    }

    // ==================== 性能统计 ====================

    private void printPerformanceStats() {
        long uptime = System.currentTimeMillis() - pluginStartTime;
        long uptimeMinutes = uptime / 60000;
        long uptimeSeconds = (uptime % 60000) / 1000;

        plugin.getLogger().info("§6========== 性能统计 ==========");
        plugin.getLogger().info(String.format("§e运行时间：%d 分 %d 秒", uptimeMinutes, uptimeSeconds));
        plugin.getLogger().info(String.format("§e触发袭击次数：%d", totalRaidsTriggered));
        plugin.getLogger().info(String.format("§e完成袭击次数：%d", totalRaidsCompleted));
        plugin.getLogger().info(String.format("§e失败袭击次数：%d", totalRaidsFailed));
        plugin.getLogger().info(String.format("§e生成怪物总数：%d", totalMobsSpawned));
        plugin.getLogger().info(String.format("§e创建 BossBar 总数：%d", totalBossBarsCreated));

        if (totalRaidsTriggered > 0) {
            double successRate = (double) totalRaidsCompleted / totalRaidsTriggered * 100;
            plugin.getLogger().info(String.format("§e袭击成功率：%.1f%%", successRate));
        }
        if (totalRaidsCompleted > 0) {
            double avgMobsPerRaid = (double) totalMobsSpawned / totalRaidsCompleted;
            plugin.getLogger().info(String.format("§e平均每波怪物数：%.1f", avgMobsPerRaid));
        }
        plugin.getLogger().info("§6================================");
    }

    public void recordRaidTriggered() { totalRaidsTriggered++; }
    public void recordRaidCompleted() { totalRaidsCompleted++; }
    public void recordRaidFailed() { totalRaidsFailed++; }
    public void recordMobSpawned() { totalMobsSpawned++; }
    public void recordBossBarCreated() { totalBossBarsCreated++; }

    public int getActiveRaidCount() {
        return raidStatesByBeacon.values().stream()
            .mapToInt(state -> state.isActive ? 1 : 0)
            .sum();
    }

    // ==================== 信标位置管理 ====================

    private void loadBeaconLocations() {
        if (plugin.getBeaconDetector() != null) {
            Location beaconLoc = plugin.getBeaconDetector().getLastDetectedBeacon();
            if (beaconLoc != null) {
                beaconLocations.put(beaconLoc.getWorld().getName(), beaconLoc.clone());
            }
        }
    }

    public void setBeaconLocation(Location beaconLocation) {
        if (beaconLocation != null && beaconLocation.getWorld() != null) {
            Location oldLocation = beaconLocations.get(beaconLocation.getWorld().getName());
            beaconLocations.put(beaconLocation.getWorld().getName(), beaconLocation.clone());

            if (oldLocation != null) {
                plugin.getLogger().info(String.format("§e[信标更新] 旧位置:(%d,%d,%d) → 新位置:(%d,%d,%d)",
                    oldLocation.getBlockX(), oldLocation.getBlockY(), oldLocation.getBlockZ(),
                    beaconLocation.getBlockX(), beaconLocation.getBlockY(), beaconLocation.getBlockZ()));
            } else {
                plugin.getLogger().info(String.format("§a[信标设置] 位置:(%d,%d,%d)",
                    beaconLocation.getBlockX(), beaconLocation.getBlockY(), beaconLocation.getBlockZ()));
            }

            startDoomDetectionTaskIfNeeded();
        }
    }

    private void startDoomDetectionTaskIfNeeded() {
        if (doomDetectionTask == null) {
            startDoomDetectionTask();
            plugin.getLogger().info("§a\u2713 灾厄信标已激活，启动灾厄效果检测任务");
        }
    }

    public boolean hasActiveRaid(Player player) {
        return bossBarManager.getBossBars().containsKey(player.getUniqueId());
    }

    // ==================== 灾厄袭击生物配置加载 ====================

    private void loadRaidMobsConfig() {
        // 委托给 RaidMobManager
        mobManager.loadRaidMobsConfig();

        // 加载全局设置（波次配置、精英配置、灾厄等级配置）
        loadGlobalSettings();
    }

    private void loadGlobalSettings() {
        ConfigManager.WaveConfig waveConfig = configManager.getWaveConfig();
        if (waveConfig != null) {
            waveOffset = waveConfig.getWaveOffset();
            easterEggWaves = waveConfig.getEasterEggWaves();
            waveDelay = waveConfig.getWaveDelay();
            mobInterval = waveConfig.getMobInterval();
        }

        ConfigManager.EliteConfig eliteConfig = configManager.getEliteConfig();
        if (eliteConfig != null) {
            Map<Integer, Double> chances = eliteConfig.getChances();
            if (chances != null) {
                mobManager.getEliteChances().clear();
                mobManager.getEliteChances().putAll(chances);
            }
        }

        ConfigManager.DoomConfig doomConfig = configManager.getDoomConfig();
        if (doomConfig != null) {
            doomLevelBonus = doomConfig.getBonusPerLevel();
        }
    }

    // ==================== 事件处理器 ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRaidTrigger(RaidTriggerEvent event) {
        Player player = event.getPlayer();
        int doomLevel = effectManager.getDoomLevel(player);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onRaidTrigger: 玩家=" + player.getName() + ", 灾厄等级=" + doomLevel + ", 取消原版事件=" + (doomLevel == EASTER_EGG_LEVEL || doomLevel >= MIN_DOOM_LEVEL));
        }

        if (doomLevel == EASTER_EGG_LEVEL || doomLevel >= MIN_DOOM_LEVEL) {
            event.setCancelled(true);

            Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), (task) -> {
                triggerSpecialRaid(player, doomLevel);
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRaidSpawnWave(RaidSpawnWaveEvent event) {
        if (!bossBarManager.getBossBars().isEmpty()) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onRaidSpawnWave: 取消原版袭击刷怪（已存在活跃BossBar）");
            }
            ((org.bukkit.event.Cancellable) event).setCancelled(true);
        }
    }

    private void startDoomDetectionTask() {
        doomDetectionTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            if (!bossBarManager.getBossBars().isEmpty()) {
                task.cancel();
                doomDetectionTask = null;
                plugin.getLogger().info("§a\u2713 灾厄袭击已触发，停止灾厄效果检测任务");
                return;
            }

            for (String worldName : beaconLocations.keySet()) {
                Location beaconLoc = beaconLocations.get(worldName);
                if (beaconLoc == null || beaconLoc.getWorld() == null) continue;

                int detectionRadius = configManager.getRaidConfig().getDetectionRadius();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (bossBarManager.getBossBars().containsKey(player.getUniqueId())) continue;
                    if (!isInRange(beaconLoc, player.getLocation(), detectionRadius)) continue;

                    int doomLevel = effectManager.getDoomLevel(player);
                    if (doomLevel != 6 && doomLevel < 7) continue;

                    final Player targetPlayer = player;
                    final int finalDoomLevel = doomLevel;
                    final Location finalBeaconLoc = beaconLoc;

                    Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (regionTask) -> {
                        if (!targetPlayer.isOnline()) return;

                        if (isInVillage(targetPlayer.getLocation())) {
                            Bukkit.getRegionScheduler().runDelayed(plugin, targetPlayer.getLocation(), (delayTask) -> {
                                if (targetPlayer.isOnline() && !bossBarManager.getBossBars().containsKey(targetPlayer.getUniqueId())) {
                                    triggerSpecialRaid(targetPlayer, finalDoomLevel);
                                }
                            }, 5L);
                        }
                    });
                }
            }
        }, 1L, 40L);

        plugin.getLogger().info("§a\u2713 灾厄效果周期性检测任务已启动");
    }

    private boolean isInRange(Location beaconLoc, Location playerLoc, int radius) {
        if (beaconLoc.getWorld() == null || playerLoc.getWorld() == null) return false;
        if (!beaconLoc.getWorld().getName().equals(playerLoc.getWorld().getName())) return false;

        int dx = Math.abs(beaconLoc.getBlockX() - playerLoc.getBlockX());
        int dy = Math.abs(beaconLoc.getBlockY() - playerLoc.getBlockY());
        int dz = Math.abs(beaconLoc.getBlockZ() - playerLoc.getBlockZ());
        return (dx + dy + dz) <= radius * 3;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof IronGolem)) return;

        IronGolem ironGolem = (IronGolem) event.getDamager();
        UUID golemUuid = ironGolem.getUniqueId();
        boolean isFriendlyDefender = false;

        for (UUID defenderUuid : defenderManager.getVillageDefenders().values()) {
            if (defenderUuid != null && defenderUuid.equals(golemUuid)) {
                isFriendlyDefender = true;
                break;
            }
        }

        if (!isFriendlyDefender) return;

        Entity victim = event.getEntity();
        if (victim instanceof Villager || victim instanceof Player || victim instanceof IronGolem) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onEntityDamageByEntity: 铁傀儡误伤已取消，受害者类型=" + victim.getType().name() + ", 伤害=" + event.getDamage());
            }
            event.setCancelled(true);
            event.setDamage(0.0);
        }
    }

    // ==================== 触发灾厄袭击 ====================

    private void triggerSpecialRaid(Player player, int doomLevel) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] triggerSpecialRaid: 玩家=" + player.getName() + ", 灾厄等级=" + doomLevel + ", 位置=(" + player.getLocation().getBlockX() + "," + player.getLocation().getBlockY() + "," + player.getLocation().getBlockZ() + ")");
        }

        recordRaidTriggered();

        if (bossBarManager.getBossBars().containsKey(player.getUniqueId())) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] triggerSpecialRaid: 玩家 " + player.getName() + " 已有活跃袭击，跳过");
            }
            return;
        }

        Location beaconLoc = null;
        String worldName = player.getWorld().getName();

        if (beaconLocations.containsKey(worldName)) {
            beaconLoc = beaconLocations.get(worldName);
        } else if (plugin.getBeaconDetector() != null) {
            beaconLoc = plugin.getBeaconDetector().getLastDetectedBeacon();
            if (beaconLoc != null) {
                beaconLocations.put(worldName, beaconLoc.clone());
            }
        }

        if (beaconLoc != null) {
            mobManager.clearSpawnLocationCache(beaconLoc);
            plugin.getLogger().fine(String.format("§e\u2734 已清除信标 (%d,%d,%d) 的旧缓存，准备重新搜索",
                beaconLoc.getBlockX(), beaconLoc.getBlockY(), beaconLoc.getBlockZ()));
        }

        Location center;
        if (beaconLoc != null) {
            center = beaconLoc.clone();
        } else {
            center = player.getLocation();
            plugin.getLogger().warning("§e\u26A0 未找到灾厄信标位置，使用玩家位置作为袭击中心（可能导致生成异常）");
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] triggerSpecialRaid: 袭击中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 信标位置=" + (beaconLoc != null ? "(" + beaconLoc.getBlockX() + "," + beaconLoc.getBlockY() + "," + beaconLoc.getBlockZ() + ")" : "未找到"));
        }

        int villageRadius = cachedVillageRadius;
        int mobsPerWave = mobManager.getMobsPerWave(doomLevel);

        int totalWaves;
        if (doomLevel == EASTER_EGG_LEVEL) {
            totalWaves = easterEggWaves;
        } else {
            totalWaves = doomLevel - waveOffset;
        }
        if (totalWaves < 1) totalWaves = 1;

        // 创建倒计时进度条 A
        BossBar countdownBar = Bukkit.createBossBar(
            String.format("§4§l灾厄袭击即将到来 §r§7- §e%d", 10),
            BarColor.RED, BarStyle.SOLID, BarFlag.CREATE_FOG
        );
        countdownBar.setProgress(1.0);
        countdownBar.setVisible(true);
        countdownBar.addPlayer(player);

        bossBarManager.getBossBars().put(player.getUniqueId(), countdownBar);

        String broadcastMessage = "§4§l【灾厄袭击】§c" + player.getName() + " 触发了 " +
            bossBarManager.getDifficultyName(doomLevel) + " 灾厄袭击！村庄将在 10 秒后遭受攻击！";

        for (Player worldPlayer : center.getWorld().getPlayers()) {
            Player targetPlayer = worldPlayer;
            Bukkit.getRegionScheduler().run(plugin, targetPlayer.getLocation(), (task) -> {
                if (targetPlayer.isOnline()) {
                    sendRaidActionBar(targetPlayer, broadcastMessage);
                }
            });
        }

        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            try {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.BAD_OMEN);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN);

                org.bukkit.potion.PotionEffect raidOmen = new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.RAID_OMEN, 200, doomLevel - 1, false, true, true
                );
                player.addPotionEffect(raidOmen);
            } catch (Exception e) {
            }
        });

        startCountdownAndRaid(player, center, doomLevel, villageRadius, totalWaves, mobsPerWave, countdownBar);
    }

    // ==================== 倒计时和袭击启动 ====================

    private void startCountdownAndRaid(Player player, Location center, int doomLevel, int radius,
                                       int totalWaves, int mobsPerWave, BossBar countdownBar) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] startCountdownAndRaid: 玩家=" + player.getName() + ", 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 灾厄等级=" + doomLevel + ", 半径=" + radius + ", 总波次=" + totalWaves + ", 每波怪物数=" + mobsPerWave);
        }
        final int[] secondsLeft = {10};

        io.papermc.paper.threadedregions.scheduler.ScheduledTask[] taskRef =
            new io.papermc.paper.threadedregions.scheduler.ScheduledTask[1];

        taskRef[0] = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            if (!player.isOnline()) {
                taskRef[0].cancel();
                return;
            }

            secondsLeft[0]--;
            countdownBar.setTitle(String.format("§4§l灾厄袭击即将到来 §r§7- §e%d", secondsLeft[0]));
            countdownBar.setProgress(secondsLeft[0] / 10.0);

            if (secondsLeft[0] <= 3 && secondsLeft[0] > 0) {
                sendRaidActionBar(player, String.format("§4§l\u274C 袭击即将开始！§r§e 还剩 %d 秒", secondsLeft[0]));
                center.getWorld().playSound(center, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }

            if (secondsLeft[0] <= 0) {
                taskRef[0].cancel();
                countdownBar.setVisible(false);

                center.getWorld().playSound(center, org.bukkit.Sound.ITEM_GOAT_HORN_SOUND_1, 1.0f, 1.0f);
                clearRaidOmenFromArea(center, radius);

                // 延迟 1 秒生成铁傀儡
                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (golemTask) -> {
                    defenderManager.spawnVillageDefender(center, radius, player);
                    defenderManager.startGolemSpawnTask(player, center, radius);
                    plugin.getLogger().fine("§e\u2734 已安排铁傀儡生成（倒计时结束后 1 秒）");
                }, 20L);

                // 延迟 2 秒预搜索
                final Location finalCenter = center.clone();
                final int finalRadius = radius;
                final Player finalPlayer = player;

                io.papermc.paper.threadedregions.scheduler.ScheduledTask preSearchTask =
                    Bukkit.getRegionScheduler().runDelayed(plugin, finalCenter, (searchTask) -> {
                        plugin.getLogger().fine(String.format("§e\u2734 开始预搜索灾厄生物生成位置... (信标: %d,%d,%d)",
                            finalCenter.getBlockX(), finalCenter.getBlockY(), finalCenter.getBlockZ()));

                        Location spawnLoc = mobManager.findValidSpawnLocation(finalCenter, finalRadius);
                        if (spawnLoc != null) {
                            plugin.getLogger().info(String.format("§a\u2713 预搜索成功！缓存生成位置: (%.1f, %.1f, %.1f)",
                                spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()));
                        } else {
                            plugin.getLogger().warning("§e\u26A0 预搜索失败！将在生成时重新搜索位置");
                        }
                        preSearchTasks.remove(finalPlayer.getUniqueId());
                    }, 40L);

                preSearchTasks.put(player.getUniqueId(), preSearchTask);
                createRaidBossBar(player, center, doomLevel, radius, totalWaves, mobsPerWave);
            }
        }, 20L, 20L);
    }

    private void clearRaidOmenFromArea(Location center, int radius) {
        Bukkit.getRegionScheduler().run(plugin, center, (task) -> {
            int clearedCount = 0;
            for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                if (entity instanceof Player) {
                    Player nearbyPlayer = (Player) entity;
                    if (nearbyPlayer.hasPotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN)) {
                        nearbyPlayer.removePotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN);
                        clearedCount++;
                    }
                }
            }
            if (clearedCount > 0 && ThreadLocalRandom.current().nextDouble() < 0.2) {
                plugin.getLogger().fine(String.format("§a\u2713 清除了 %d 个玩家的袭击之兆效果", clearedCount));
            }
        });
    }

    // ==================== ActionBar 消息 ====================

    public void sendRaidActionBar(Player player, String message) {
        if (!player.isOnline()) return;

        try {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));

            Location playerLocation = player.getLocation().clone();
            Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                if (player.isOnline()) {
                    player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(""));
                }
            }, 60L);
        } catch (Exception e) {
            plugin.getLogger().fine("§e\u26A0 发送 ActionBar 失败：" + e.getMessage());
        }
    }

    public void broadcastRaidActionBar(Location center, double radius, String message) {
        if (center == null || center.getWorld() == null) return;

        Bukkit.getRegionScheduler().run(plugin, center, (task) -> {
            try {
                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof Player) {
                        sendRaidActionBar((Player) entity, message);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().fine("§e\u26A0 广播 ActionBar 失败：" + e.getMessage());
            }
        });
    }

    // ==================== 创建袭击进度条 ====================

    private void createRaidBossBar(Player player, Location center, int doomLevel, int radius,
                                   int totalWaves, int mobsPerWave) {
        RaidState raidState = new RaidState(totalWaves, mobsPerWave, waveDelay, mobInterval, doomLevel, center, player.getUniqueId());
        raidStates.put(player.getUniqueId(), raidState);

        String raidName = String.format("§4§l灾厄袭击 §r§7- %s §r§c波次：%d/%d",
            bossBarManager.getDifficultyName(doomLevel), 0, totalWaves);
        BossBar bossBar = Bukkit.createBossBar(raidName, BarColor.RED, BarStyle.SOLID, BarFlag.CREATE_FOG);
        bossBar.setProgress(0.0);
        bossBar.setVisible(true);
        bossBar.addPlayer(player);

        recordBossBarCreated();

        bossBarManager.getBossBars().put(player.getUniqueId(), bossBar);

        startRaidScheduler(player, center, doomLevel, radius, raidState, bossBar);

        if (doomLevel == 6) {
            fogEffectManager.startGreenFogEffect(player, center, 50);
        } else if (doomLevel >= MIN_DOOM_LEVEL && doomLevel <= MAX_NORMAL_DOOM_LEVEL) {
            fogEffectManager.startWhiteFogEffect(player, center, 50);
        }
    }

    // ==================== 村民检测 ====================

    private int countVillagersInVillage(Location center, int radius) {
        if (center == null || center.getWorld() == null) return 0;

        String cacheKey = String.format("%s:%d:%d:%d:%d",
            center.getWorld().getName(),
            center.getBlockX(), center.getBlockY(), center.getBlockZ(), radius);

        long currentTimeMillis = System.currentTimeMillis();

        // 检查缓存（从 RaidMobManager 获取）
        Map<String, RaidMobManager.VillagerCacheEntry> villagerCountCache = mobManager.getVillagerCountCache();
        RaidMobManager.VillagerCacheEntry cachedEntry = villagerCountCache.get(cacheKey);
        if (cachedEntry != null && (currentTimeMillis - cachedEntry.timestamp) < 500L) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] countVillagersInVillage: 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 半径=" + radius + ", 村民数=" + cachedEntry.count + "（缓存）");
            }
            return cachedEntry.count;
        }

        try {
            Collection<Entity> entities = center.getWorld().getNearbyEntities(center, radius, radius, radius);
            int count = 0;
            for (Entity entity : entities) {
                if (entity instanceof Villager && entity.isValid() && !entity.isDead()) {
                    count++;
                }
            }

            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] countVillagersInVillage: 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 半径=" + radius + ", 村民数=" + count);
            }

            villagerCountCache.put(cacheKey, new RaidMobManager.VillagerCacheEntry(count, currentTimeMillis));
            return count;
        } catch (IllegalStateException e) {
            plugin.getLogger().warning(String.format("§e\u26A0 countVillagersInVillage 未在正确的区域线程中调用！位置：%s", center.toString()));
            return 0;
        }
    }

    // ==================== 袭击调度器 ====================

    private void startRaidScheduler(Player player, Location center, int doomLevel, int radius,
                                    RaidState raidState, BossBar bossBar) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] startRaidScheduler: 玩家=" + player.getName() + ", 灾厄等级=" + doomLevel + ", 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 总波次=" + raidState.totalWaves + ", 每波怪物数=" + raidState.mobsPerWave);
        }

        List<String> mobTypes = mobManager.getRaidMobs().get(doomLevel);

        if (mobTypes == null || mobTypes.isEmpty()) {
            plugin.getLogger().warning(String.format("§c\u2717 未找到灾厄等级 %d 的生物列表，袭击无法开始！", doomLevel));
            Bukkit.getGlobalRegionScheduler().run(plugin, (t) -> {
                endRaidWithFailure(player, bossBar, raidState);
            });
            return;
        }

        Bukkit.getRegionScheduler().run(plugin, center, (initTask) -> {
            raidState.initialVillagerCount = countVillagersInVillage(center, radius);
            if (raidState.initialVillagerCount == 0) {
                plugin.getLogger().warning("§e\u26A0 村庄内没有村民，袭击可能无法正常进行");
            }
        });

        final long[] tickCounter = {0};

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            if (!player.isOnline()) {
                plugin.getLogger().fine(String.format("§e\u26A0 玩家 %s 已退出，灾厄袭击暂停", player.getName()));
                return;
            }

            if (!raidState.isActive) {
                task.cancel();
                return;
            }

            long currentTime = System.currentTimeMillis();
            tickCounter[0]++;

            // 定期检测村民
            if (tickCounter[0] % villagerCheckInterval == 0) {
                Bukkit.getRegionScheduler().run(plugin, center, (villagerCheckTask) -> {
                    int currentVillagerCount = countVillagersInVillage(center, radius);

                    if (raidState.initialVillagerCount > 0 && currentVillagerCount == 0) {
                        plugin.getLogger().warning("§c\u2717 村庄内所有村民已死亡！袭击失败！");
                        Bukkit.getGlobalRegionScheduler().run(plugin, (failTask) -> {
                            endRaidWithFailure(player, bossBar, raidState);
                        });
                    } else if (currentVillagerCount < raidState.initialVillagerCount) {
                        plugin.getLogger().fine(String.format("§e\u26A0 村民死亡：%d/%d 存活",
                            currentVillagerCount, raidState.initialVillagerCount));
                    }
                });
            }

            // 更新 BossBar
            if (tickCounter[0] % bossBarUpdateInterval == 0) {
                Bukkit.getGlobalRegionScheduler().run(plugin, (t) -> {
                    if (!player.isOnline()) return;
                    bossBarManager.updateBossBarForWave(bossBar, raidState, doomLevel);
                });
            }

            // 施加 Buff
            ConfigManager.CombatBuffConfig combatBuffConfig = configManager.getCombatBuffConfig();
            if (combatBuffConfig != null && combatBuffConfig.isEnabled()) {
                long buffInterval = combatBuffConfig.getInterval();
                if (tickCounter[0] % buffInterval == 0 && raidState.currentWave > 0) {
                    Bukkit.getRegionScheduler().run(plugin, center, (buffTask) -> {
                        applyBuffToPlayers(center, doomLevel);
                    });
                }
            }

            // 检查是否可以开始下一波
            if (raidState.currentWave < raidState.totalWaves) {
                if (raidState.currentWave > 0 && currentTime - raidState.lastSpawnTime < raidState.waveDelay * 50) {
                    return;
                }

                if (raidState.currentWave > 0 && !bossBarManager.areAllMobsDead()) {
                    return;
                }

                final boolean[] shouldContinue = {true};

                Bukkit.getRegionScheduler().run(plugin, center, (villagerCheckTask) -> {
                    try {
                        int currentVillagerCount = countVillagersInVillage(center, radius);

                        if (raidState.initialVillagerCount > 0 && currentVillagerCount == 0) {
                            plugin.getLogger().warning("§c\u2717 村庄内所有村民已死亡！袭击失败！");
                            shouldContinue[0] = false;
                            Bukkit.getGlobalRegionScheduler().run(plugin, (failTask) -> {
                                endRaidWithFailure(player, bossBar, raidState);
                            });
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e\u26A0 检测村民失败：" + e.getMessage());
                    }

                    if (!shouldContinue[0]) return;

                    raidState.currentWave++;
                    raidState.spawnedThisWave = 0;
                    raidState.lastSpawnTime = currentTime;

                    bossBarManager.updateBossBarForWave(bossBar, raidState, doomLevel);

                    int checkedCount = 0;
                    Location finalCenter = center.clone();
                    int finalRadius = radius;
                    int currentWaveNum = raidState.currentWave;
                    int mobsPerWaveCount = raidState.mobsPerWave;

                    for (Player p : finalCenter.getWorld().getPlayers()) {
                        if (checkedCount++ > maxPlayerCheckCount) break;
                        Player targetPlayer = p;
                        Bukkit.getRegionScheduler().run(plugin, targetPlayer.getLocation(), (playerTask) -> {
                            if (targetPlayer.isOnline() && targetPlayer.getLocation().distance(finalCenter) <= finalRadius * 2) {
                                sendRaidActionBar(targetPlayer,
                                    String.format("§4§l\u274C 第 %d 波袭击 §r§7 - §e准备迎击 %d 只怪物！",
                                        currentWaveNum, mobsPerWaveCount));
                            }
                        });
                    }

                    Bukkit.getRegionScheduler().run(plugin, center, (spawnTask) -> {
                        mobManager.spawnWaveMobs(center, doomLevel, radius, mobTypes, raidState);
                    });
                });

                return;
            } else {
                if (bossBarManager.areAllMobsDead()) {
                    Bukkit.getRegionScheduler().run(plugin, center, (villagerCheckTask) -> {
                        int currentVillagerCount = countVillagersInVillage(center, radius);

                        if (raidState.initialVillagerCount > 0 && currentVillagerCount == 0) {
                            plugin.getLogger().fine("§c\u2717 村庄内所有村民已死亡！袭击失败！");
                            Bukkit.getGlobalRegionScheduler().run(plugin, (failTask) -> {
                                endRaidWithFailure(player, bossBar, raidState);
                            });
                        } else {
                            task.cancel();
                            endRaid(player, bossBar, raidState);
                        }
                    });
                }
                return;
            }
        }, raidSchedulerInterval, raidSchedulerInterval);

        raidState.isActive = true;

        // ✅ 启动快速村民死亡检测（每2tick检查一次所有活跃袭击的村民数量）
        startVillagerDeathCheckTask();
    }

    /**
     * ✅ 快速村民死亡检测任务
     * 每20tick（1秒）检查一次所有活跃袭击的村民数量，发现0村民时立即结束袭击
     * 完全独立于 raidScheduler，不由死亡事件触发，避免线程安全问题和实体移除时序问题
     * 
     * 注意：此任务永久运行，不自动取消。无活跃袭击时只是空迭代，开销接近0。
     * 如果有自取消逻辑，会因 raidScheduler 延迟大于1tick而提前取消任务（时序竞态）。
     */
    private void startVillagerDeathCheckTask() {
        // 如果已有任务在运行，不重复启动
        if (villagerDeathCheckTask != null && !villagerDeathCheckTask.isCancelled()) {
            return;
        }

        villagerDeathCheckTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Map.Entry<Location, RaidState> entry : raidStatesByBeacon.entrySet()) {
                RaidState raidState = entry.getValue();
                if (!raidState.isActive) continue;

                Location beaconLoc = entry.getKey();
                if (beaconLoc == null || beaconLoc.getWorld() == null) continue;

                // 在信标区域线程中扫描村民数量
                Bukkit.getRegionScheduler().run(plugin, beaconLoc, (regionTask) -> {
                    if (!raidState.isActive) return;
                    if (raidState.initialVillagerCount <= 0) return;

                    int remaining = countVillagersInVillage(beaconLoc, cachedVillageRadius);

                    if (configManager.isDebugEnabled()) {
                        plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] 快速村民检测: 信标=("
                            + beaconLoc.getBlockX() + "," + beaconLoc.getBlockY() + "," + beaconLoc.getBlockZ()
                            + "), 剩余村民=" + remaining + "/" + raidState.initialVillagerCount);
                    }

                    if (remaining <= 0) {
                        // 找到对应玩家并结束袭击
                        for (Map.Entry<UUID, RaidState> stateEntry : raidStates.entrySet()) {
                            if (stateEntry.getValue() == raidState) {
                                Player player = Bukkit.getPlayer(stateEntry.getKey());
                                BossBar bossBar = bossBarManager.getBossBars().get(stateEntry.getKey());
                                if (player != null && bossBar != null) {
                                    plugin.getLogger().warning("§c\u2717 村庄内所有村民已死亡！立即结束袭击！");
                                    Bukkit.getGlobalRegionScheduler().run(plugin, (failTask) -> {
                                        endRaidWithFailure(player, bossBar, raidState);
                                    });
                                }
                                break;
                            }
                        }
                    }
                });
            }
        }, 1L, 20L); // ✅ 初始延迟1L（Folia要求≥1），每20tick（1秒）扫描一次
    }

    // ==================== Buff 效果 ====================

    private void applyBuffToPlayers(Location center, int doomLevel) {
        ConfigManager.CombatBuffConfig combatBuffConfig = configManager.getCombatBuffConfig();
        if (combatBuffConfig == null || !combatBuffConfig.isEnabled()) return;

        Location beaconLocation = beaconLocations.get(center.getWorld().getName());
        if (beaconLocation == null) beaconLocation = center.clone();

        final Location finalBeaconLocation = beaconLocation;
        final double range = combatBuffConfig.getRange();
        final int duration = combatBuffConfig.getDuration() * 20;

        Bukkit.getRegionScheduler().run(plugin, finalBeaconLocation, (task) -> {
            try {
                Collection<Player> nearbyPlayers = new ArrayList<>();
                for (Entity entity : finalBeaconLocation.getWorld().getNearbyEntities(
                    finalBeaconLocation, range, range, range,
                    entity2 -> entity2 instanceof Player && entity2.isValid()
                )) {
                    if (entity instanceof Player) {
                        nearbyPlayers.add((Player) entity);
                    }
                }

                if (nearbyPlayers.isEmpty()) return;

                int[] appliedCount = {0};
                for (Player player : nearbyPlayers) {
                    if (!player.isOnline() || player.isDead()) continue;
                    
                    // ✅ 每个玩家在各自区域线程中施加药水效果
                    Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (playerTask) -> {
                        if (!player.isOnline() || player.isDead()) return;
                        if (player.getLocation().distance(finalBeaconLocation) > range) return;
                        
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.REGENERATION, duration, 1, true, true
                        ));
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.SLOW_FALLING, duration, 0, true, true
                        ));
                        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                            org.bukkit.potion.PotionEffectType.STRENGTH, duration, 1, true, true
                        ));
                        appliedCount[0]++;
                    });
                }

                if (appliedCount[0] > 0) {
                    try {
                        finalBeaconLocation.getWorld().playSound(finalBeaconLocation, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                    } catch (Exception e) {
                        finalBeaconLocation.getWorld().playSound(finalBeaconLocation, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    }
                    finalBeaconLocation.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, finalBeaconLocation, 30, 5.0, 5.0, 5.0, 0.05);
                }
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("§e\u26A0 施加 Buff 失败：%s", e.getMessage()));
            }
        });
    }

    // ==================== 结束袭击 ====================

    private void endRaid(Player player, BossBar bossBar, RaidState raidState) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] endRaid: 玩家=" + player.getName() + ", 灾厄等级=" + raidState.originalDoomLevel + ", 完成波次=" + raidState.currentWave + "/" + raidState.totalWaves);
        }
        recordRaidCompleted();
        raidState.isActive = false;

        // 清理铁傀儡生成任务
        io.papermc.paper.threadedregions.scheduler.ScheduledTask golemTask =
            defenderManager.getGolemSpawnTasks().remove(player.getUniqueId());
        if (golemTask != null) {
            golemTask.cancel();
            plugin.getLogger().info("§6【清理】已取消铁傀儡生成任务");
        }

        // 清理预搜索任务
        io.papermc.paper.threadedregions.scheduler.ScheduledTask preSearchTask =
            preSearchTasks.remove(player.getUniqueId());
        if (preSearchTask != null) {
            preSearchTask.cancel();
            plugin.getLogger().fine("§6【清理】已取消预搜索任务");
        }

        // 清理迷雾效果
        fogEffectManager.stopWhiteFogEffect(player.getUniqueId());
        fogEffectManager.stopGreenFogEffect(player.getUniqueId());

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> {
            bossBarManager.removeBossBar(bossBar);
            bossBarManager.getBossBars().remove(player.getUniqueId());
            raidStates.remove(player.getUniqueId());

            // 清理怪物相关缓存
            mobManager.getActiveRaidMobs().clear();
            mobManager.getMobSearchOffset().clear();
            mobManager.getLastTargetSearchTime().clear();
            mobManager.getCreeperCheckOffset().clear();
            mobManager.getCachedFollowRanges().clear();
            mobManager.getVillagerCountCache().clear();

            Location beaconLoc = beaconLocations.get(player.getWorld().getName());
            if (beaconLoc != null) {
                mobManager.clearSpawnLocationCache(beaconLoc);
            }

            int doomLevel = raidState.originalDoomLevel;
            final int finalDoomLevel = doomLevel;

            Location raidBeaconLocation = beaconLocations.get(player.getWorld().getName());
            if (raidBeaconLocation != null) {
                Bukkit.getRegionScheduler().run(plugin, raidBeaconLocation, (regionTask) -> {
                    giveHeroOfTheVillageToAllNearbyPlayers(raidBeaconLocation, player, finalDoomLevel);
                });
            } else {
                Location playerLocation = player.getLocation();
                Bukkit.getRegionScheduler().run(plugin, playerLocation, (regionTask) -> {
                    giveHeroOfTheVillageToAllNearbyPlayers(playerLocation, player, finalDoomLevel);
                });
            }
        }, 60L);
    }

    private void endRaidWithFailure(Player player, BossBar bossBar, RaidState raidState) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] endRaidWithFailure: 玩家=" + player.getName() + ", 灾厄等级=" + raidState.originalDoomLevel + ", 完成波次=" + raidState.currentWave + "/" + raidState.totalWaves);
        }
        recordRaidFailed();
        raidState.isActive = false;

        io.papermc.paper.threadedregions.scheduler.ScheduledTask golemTask =
            defenderManager.getGolemSpawnTasks().remove(player.getUniqueId());
        if (golemTask != null) {
            golemTask.cancel();
            plugin.getLogger().info("§6【清理】已取消铁傀儡生成任务");
        }

        io.papermc.paper.threadedregions.scheduler.ScheduledTask preSearchTask =
            preSearchTasks.remove(player.getUniqueId());
        if (preSearchTask != null) {
            preSearchTask.cancel();
            plugin.getLogger().fine("§6【清理】已取消预搜索任务");
        }

        fogEffectManager.stopWhiteFogEffect(player.getUniqueId());
        fogEffectManager.stopGreenFogEffect(player.getUniqueId());

        player.sendTitle(
            "§c§l村民已全部死于灾厄",
            "§7",
            titleFailFirstFadeIn, titleFailFirstDisplay, titleFailFirstFadeOut
        );

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> {
            if (!player.isOnline()) return;

            bossBarManager.removeBossBar(bossBar);
            bossBarManager.getBossBars().remove(player.getUniqueId());
            raidStates.remove(player.getUniqueId());

            mobManager.getActiveRaidMobs().clear();
            mobManager.getMobSearchOffset().clear();
            mobManager.getLastTargetSearchTime().clear();
            mobManager.getCreeperCheckOffset().clear();
            mobManager.getCachedFollowRanges().clear();
            mobManager.getVillagerCountCache().clear();

            Location beaconLoc = beaconLocations.get(player.getWorld().getName());
            if (beaconLoc != null) {
                mobManager.clearSpawnLocationCache(beaconLoc);
            }

            plugin.getLogger().warning("§c✗ 灾厄袭击失败 - 村庄内所有村民已死亡！");

            player.sendTitle(
                "§c§l我们失败了",
                "§7村庄已被灾厄笼罩\n§e灾厄力量正在蔓延...",
                titleFailSecondFadeIn, titleFailSecondDisplay, titleFailSecondFadeOut
            );

            plugin.getLogger().info("§e\u2713 已保留灾厄袭击生物（它们将继续在村庄游荡）");
        }, titleFailDelay);
    }

    // ==================== 村庄英雄效果 ====================

    private void giveHeroOfTheVillage(Player player, int doomLevel) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] giveHeroOfTheVillage: 玩家=" + player.getName() + ", 灾厄等级=" + doomLevel);
        }
        if (!player.isOnline()) return;

        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BAD_OMEN);
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN);

        int amplifier = doomLevel - 1;
        ConfigManager.RaidConfig raidConfig = configManager.getRaidConfig();
        int durationSeconds = raidConfig != null ? raidConfig.getHeroEffectDuration() : 600;
        int duration = durationSeconds * 20;

        org.bukkit.potion.PotionEffect heroEffect = new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE, duration, amplifier, false, true, true
        );
        player.addPotionEffect(heroEffect);

        String romanNumeral = bossBarManager.getRomanNumeral(amplifier + 1);
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (t) -> {
            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                "§b§l村庄英雄 " + romanNumeral + " §r§e - 你成功保卫了村庄！§7（3 分钟）"));
        });

        lootManager.giveRaidLoot(player, doomLevel);
    }

    private void giveHeroOfTheVillageToAllNearbyPlayers(Location centerLocation, Player triggerPlayer, int doomLevel) {
        ConfigManager.BeaconConfig beaconConfig = configManager.getBeaconConfig();
        int range = beaconConfig != null ? beaconConfig.getMaxRange() : 50;

        List<Player> nearbyPlayers = new ArrayList<>();
        for (Player onlinePlayer : centerLocation.getWorld().getPlayers()) {
            if (onlinePlayer.getLocation().distance(centerLocation) <= range) {
                nearbyPlayers.add(onlinePlayer);
            }
        }

        int amplifier = doomLevel - 1;
        ConfigManager.RaidConfig raidConfig = configManager.getRaidConfig();
        int durationSeconds = raidConfig != null ? raidConfig.getHeroEffectDuration() : 600;
        int duration = durationSeconds * 20;
        String romanNumeral = bossBarManager.getRomanNumeral(amplifier + 1);

        for (Player recipient : nearbyPlayers) {
            if (!recipient.isOnline()) continue;

            // ✅ 每个玩家在各自区域线程中施加村庄英雄效果
            Bukkit.getRegionScheduler().run(plugin, recipient.getLocation(), (regionTask) -> {
                if (!recipient.isOnline()) return;

                recipient.removePotionEffect(org.bukkit.potion.PotionEffectType.BAD_OMEN);
                recipient.removePotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN);

                org.bukkit.potion.PotionEffect heroEffect = new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE, duration, amplifier, false, true, true
                );
                recipient.addPotionEffect(heroEffect);

                recipient.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                    "§b§l村庄英雄 " + romanNumeral + " §r§e - 你们成功保卫了村庄！§7（3 分钟）"));

                Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (delayTask) -> {
                    if (!recipient.isOnline()) return;

                    String secondaryMessage;
                    if (recipient.equals(triggerPlayer)) {
                        secondaryMessage = "§6[灾厄袭击] §a\u2713 你成功触发了灾厄袭击并获胜！";
                    } else {
                        secondaryMessage = "§6[灾厄袭击] §a\u2713 " + triggerPlayer.getName() + " 成功击退了灾厄袭击！";
                    }

                    recipient.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(secondaryMessage));

                    Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (clearTask) -> {
                        if (!recipient.isOnline()) return;
                        recipient.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(""));
                    }, 60L);
                }, 60L);
            });

            if (recipient.equals(triggerPlayer)) {
                lootManager.giveRaidLoot(recipient, doomLevel);
            }
        }

        String message = String.format("§b§l【村庄英雄 %s】§r§e%s §r§7成功击退了 %d 级灾厄袭击，为附近的玩家带来了村庄英雄 %s 效果！",
            romanNumeral, triggerPlayer.getName(), doomLevel, romanNumeral);
        for (Player worldPlayer : centerLocation.getWorld().getPlayers()) {
            if (!nearbyPlayers.contains(worldPlayer)) {
                sendRaidActionBar(worldPlayer, message);
            }
        }
    }

    // ==================== 村庄检测 ====================

    private boolean isInVillage(Location location) {
        int villageRadius = cachedVillageRadius;

        try {
            Collection<Villager> villagers = mobManager.findNearbyEntities(location, villageRadius, Villager.class);
            boolean result = !villagers.isEmpty();
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] isInVillage: 位置=(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + "), 半径=" + villageRadius + ", 村民数=" + villagers.size() + ", 结果=" + result);
            }
            return result;
        } catch (IllegalStateException e) {
            plugin.getLogger().warning(String.format("§e\u26A0 isInVillage 方法未在正确的区域线程中调用！位置：%s", location.toString()));
            return false;
        }
    }

    // ==================== 手动调试 ====================

    public void triggerManualCreeperCheck(Location location) {
        mobManager.triggerManualCreeperCheck(location);
    }

    // ==================== 事件处理器 续 ====================

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(org.bukkit.event.entity.EntityDeathEvent event) {
        Entity entity = event.getEntity();
        UUID entityId = entity.getUniqueId();

        if (entity instanceof Villager) {
            // ✅ 清除村民数量缓存，仅用于后续扫描
            mobManager.getVillagerCountCache().clear();
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] 村民死亡: 位置=("
                    + entity.getLocation().getBlockX() + "," + entity.getLocation().getBlockY() + "," + entity.getLocation().getBlockZ() + ")");
            }
        }

        if (mobManager.getActiveRaidMobs().contains(entityId)) {
            mobManager.getActiveRaidMobs().remove(entityId);
            mobManager.getMobSearchOffset().remove(entityId);
            mobManager.getLastTargetSearchTime().remove(entityId);

            io.papermc.paper.threadedregions.scheduler.ScheduledTask healthTask =
                defenderManager.getActiveHealthTasks().remove(entityId);
            if (healthTask != null) {
                healthTask.cancel();
            }

            // 事件驱动：找到所属袭击，减少存活计数，如果归零则推进波次
            for (RaidState rs : raidStates.values()) {
                if (rs.isActive && rs.raidMobs.remove(entityId)) {
                    int remaining = rs.aliveMobs.decrementAndGet();
                    if (remaining <= 0 && rs.spawnedThisWave >= rs.mobsPerWave && rs.currentWave < rs.totalWaves) {
                        // 本波怪物全部死亡 → 调度到袭击中心区域推进波次
                        Bukkit.getRegionScheduler().run(plugin, rs.raidCenter, (task) -> {
                            if (!rs.isActive) return;
                            rs.currentWave++;
                            rs.spawnedThisWave = 0;
                            rs.lastSpawnTime = System.currentTimeMillis();
                            // 触发下一波
                            List<String> nextMobs = mobManager.getRaidMobs().get(rs.originalDoomLevel);
                            if (nextMobs != null) {
                                mobManager.spawnWaveMobs(rs.raidCenter, rs.originalDoomLevel,
                                    cachedVillageRadius, nextMobs, rs);
                            }
                        });
                    } else if (remaining <= 0 && rs.spawnedThisWave >= rs.mobsPerWave && rs.currentWave >= rs.totalWaves) {
                        // 最后一波完成
                        BossBar bar = bossBarManager.getBossBars().get(rs.playerId);
                        Player raidPlayer = Bukkit.getPlayer(rs.playerId);
                        if (bar != null && raidPlayer != null) {
                            Bukkit.getRegionScheduler().run(plugin, rs.raidCenter, (task) -> {
                                endRaid(raidPlayer, bar, rs);
                            });
                        }
                    }
                    break;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        BossBar bossBar = bossBarManager.getBossBars().get(playerUuid);
        RaidState raidState = raidStates.get(playerUuid);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onPlayerJoin: 玩家=" + player.getName() + ", 有未完成袭击=" + (bossBar != null && raidState != null && raidState.isActive));
        }

        if (bossBar != null && raidState != null && raidState.isActive) {
            plugin.getLogger().info(String.format("§a\u2713 玩家 %s 重新加入，恢复灾厄袭击", player.getName()));

            Bukkit.getGlobalRegionScheduler().run(plugin, (task) -> {
                bossBar.addPlayer(player);
                bossBar.setVisible(true);
                plugin.getLogger().fine(String.format("§a\u2713 已恢复玩家 %s 的 BossBar 显示", player.getName()));
                bossBarManager.updateBossBarForWave(bossBar, raidState, raidState.totalWaves + 5);
            });

            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                if (player.isOnline()) {
                    sendRaidActionBar(player, "§6【灾厄袭击】§e你还有未完成的灾厄袭击！");
                    sendRaidActionBar(player, String.format("§e 当前波次：%d/%d §7| §e剩余怪物：%d",
                        raidState.currentWave, raidState.totalWaves, bossBarManager.countAliveMobs()));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        BossBar bar = bossBarManager.getBossBars().remove(uid);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }
        RaidState rs = raidStates.get(uid);
        if (rs != null && rs.isActive) {
            rs.isActive = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof Slime) {
            Slime slime = (Slime) entity;
            Location spawnLoc = entity.getLocation();

            Collection<Entity> nearby = spawnLoc.getWorld().getNearbyEntities(
                spawnLoc, 2, 2, 2,
                e -> e instanceof Slime && mobManager.getActiveRaidMobs().contains(e.getUniqueId())
            );

            if (!nearby.isEmpty()) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onEntitySpawn: 灾厄史莱姆分裂产生小史莱姆，已加入袭击列表");
                }
                mobManager.getActiveRaidMobs().add(entity.getUniqueId());

                Bukkit.getRegionScheduler().run(plugin, spawnLoc, (task) -> {
                    if (entity.isValid() && !entity.isDead()) {
                        entity.setGlowing(true);
                        String mobName = mobManager.getMobNames().getOrDefault(
                            entity.getType().name(), entity.getType().name()
                        );
                        entity.setCustomName("§4§l灾厄" + mobName);
                        entity.setCustomNameVisible(true);
                    }
                });

                plugin.getLogger().fine(String.format("§a\u2713 灾厄史莱姆分裂产生小史莱姆，已加入袭击列表"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(org.bukkit.event.entity.EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof Creeper)) return;

        Creeper creeper = (Creeper) entity;
        if (!mobManager.getActiveRaidMobs().contains(creeper.getUniqueId())) return;

        Location explodeLoc = entity.getLocation();
        Collection<Entity> nearby = explodeLoc.getWorld().getNearbyEntities(
            explodeLoc, 4, 4, 4,
            e -> (e instanceof Villager || e instanceof IronGolem) && !e.isDead()
        );

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onEntityExplode: 苦力怕位置=(" + explodeLoc.getBlockX() + "," + explodeLoc.getBlockY() + "," + explodeLoc.getBlockZ() + "), 附近目标数=" + nearby.size() + ", 取消爆炸=" + nearby.isEmpty());
        }

        if (!nearby.isEmpty()) {
            plugin.getLogger().fine(String.format("§e\u2734 灾厄苦力怕在村民/铁傀儡附近爆炸（%d个目标）", nearby.size()));
        } else {
            event.setCancelled(true);
            plugin.getLogger().fine("§e\u2734 灾厄苦力怕爆炸被取消（附近无村民/铁傀儡）");
        }
    }

    // ==================== 访问器（供管理器调用） ====================

    public Map<String, Location> getBeaconLocations() { return beaconLocations; }
    public Map<UUID, RaidState> getRaidStates() { return raidStates; }
    public Map<Location, RaidState> getRaidStatesByBeacon() { return raidStatesByBeacon; }

    public Location getBeaconLocation(String worldName) { return beaconLocations.get(worldName); }
    public double getCachedHealthMultiplier() { return cachedHealthMultiplier; }
    public double getCachedDamageMultiplier() { return cachedDamageMultiplier; }
    public double getCachedSpeedMultiplier() { return cachedSpeedMultiplier; }
    public int getCachedFollowRange() { return cachedFollowRange; }
    public int getCachedVillageRadius() { return cachedVillageRadius; }
    public double getDoomLevelBonus() { return doomLevelBonus; }
    public double getDefenderScaleMultiplier() { return defenderScaleMultiplier; }
    public long getWaveDelay() { return waveDelay; }
    public long getMobInterval() { return mobInterval; }

    // 提供给其他类调用的非私有触发器
    public void triggerSpecialRaidPublic(Player player, int doomLevel) {
        triggerSpecialRaid(player, doomLevel);
    }
}
