package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import net.kyori.adventure.text.Component;

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
    private final RaidScheduler raidScheduler;

    // 存储每个世界的灾厄信标位置（世界名称 -> 信标方块位置）
    /** 每个玩家最近摸的信标位置（player UUID -> 信标），并发袭击各自以自己信标为中心 */
    private final Map<UUID, Location> beaconLocations = new ConcurrentHashMap<>();

    // 存储每个灾厄信标的袭击状态（信标位置 -> 袭击状态）
    private final Map<Location, RaidState> raidStatesByBeacon = new ConcurrentHashMap<>();

    // 兼容旧代码：玩家 UUID -> RaidState
    private final Map<UUID, RaidState> raidStates = new ConcurrentHashMap<>();

    // ✅ 配置字段
    private int waveOffset;
    private int easterEggWaves;
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

    // ✅ 快速村民死亡检测任务（每20tick检查一次，平衡性能与即时性）
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask villagerDeathCheckTask;

    // ✅ 预搜索任务管理（玩家 UUID -> ScheduledTask）
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> preSearchTasks = new ConcurrentHashMap<>();

    // ✅ 袭击调度器任务管理（玩家 UUID -> ScheduledTask），退出时取消、重进时恢复
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> raidSchedulerTasks = new ConcurrentHashMap<>();

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
        this.raidScheduler = new RaidScheduler(plugin, this);

        // 使用 ConfigManager 获取实体配置
        org.yinwu.config.EntityConfig entityConfig = configManager.getEntityConfig();
        this.cachedHealthMultiplier = entityConfig.getHealthMultiplier();
        this.cachedDamageMultiplier = entityConfig.getDamageMultiplier();
        this.cachedSpeedMultiplier = entityConfig.getSpeedMultiplier();
        this.cachedFollowRange = entityConfig.getFollowRange();

        org.yinwu.config.RaidConfig raidConfig = configManager.getRaidConfig();
        this.cachedVillageRadius = raidConfig != null ? raidConfig.getVillageRadius() : 48;

        loadDebugConfig();
        loadPerformanceConfig();
        loadRaidMobsConfig();

        // 注意：周期性任务现在在管理器构造函数中启动
        // 灾厄效果检测任务改为信标激活后启动
    }

    // ==================== 配置加载 ====================

    private void loadDebugConfig() {
        debugEnabled = configManager.isDebugEnabled();
        creeperDetectionDebug = true;
        org.yinwu.config.DebugConfig debugConfig = configManager.getDebugConfig();
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
        org.yinwu.config.RaidPerformanceConfig perfConfig = configManager.getRaidPerformanceConfig();
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

            org.yinwu.config.TitleTimingConfig titleConfig = perfConfig.getTitleTiming();
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

        org.yinwu.config.LootConfig lootConfig = configManager.getLootConfig();
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

        // 2.1 取消灾厄检测任务（去重任务，可复用）
        raidScheduler.cleanup();

        // 2.2 取消所有袭击调度器任务
        for (io.papermc.paper.threadedregions.scheduler.ScheduledTask task : raidSchedulerTasks.values()) {
            if (task != null && !task.isCancelled()) {
                task.cancel();
            }
        }
        raidSchedulerTasks.clear();

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

        org.yinwu.config.EntityConfig entityConfig = configManager.getEntityConfig();
        this.cachedHealthMultiplier = entityConfig.getHealthMultiplier();
        this.cachedDamageMultiplier = entityConfig.getDamageMultiplier();
        this.cachedSpeedMultiplier = entityConfig.getSpeedMultiplier();
        this.cachedFollowRange = entityConfig.getFollowRange();

        org.yinwu.config.RaidConfig raidConfig = configManager.getRaidConfig();
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
        return (int) raidStates.values().stream()
            .filter(state -> state.isActive)
            .count();
    }

    // ==================== 信标位置管理 ====================

    /** 记录玩家摸过的信标（per-player，避免并发袭击中心串扰） */
    public void setBeaconLocation(Player player, Location beaconLocation) {
        if (player != null && beaconLocation != null && beaconLocation.getWorld() != null) {
            UUID pUid = player.getUniqueId();
            Location oldLocation = beaconLocations.get(pUid);
            beaconLocations.put(pUid, beaconLocation.clone());

            if (oldLocation != null) {
                plugin.getLogger().info(String.format("§e[信标更新] 旧位置:(%d,%d,%d) → 新位置:(%d,%d,%d)",
                    oldLocation.getBlockX(), oldLocation.getBlockY(), oldLocation.getBlockZ(),
                    beaconLocation.getBlockX(), beaconLocation.getBlockY(), beaconLocation.getBlockZ()));
            } else {
                plugin.getLogger().info(String.format("§a[信标设置] 位置:(%d,%d,%d)",
                    beaconLocation.getBlockX(), beaconLocation.getBlockY(), beaconLocation.getBlockZ()));
            }

            raidScheduler.startDoomDetectionTask();
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
        org.yinwu.config.WaveConfig waveConfig = configManager.getWaveConfig();
        if (waveConfig != null) {
            waveOffset = waveConfig.getWaveOffset();
            easterEggWaves = waveConfig.getEasterEggWaves();
            waveDelay = waveConfig.getWaveDelay();
            mobInterval = waveConfig.getMobInterval();
        }

        org.yinwu.config.EliteConfig eliteConfig = configManager.getEliteConfig();
        if (eliteConfig != null) {
            Map<Integer, Double> chances = eliteConfig.getChances();
            if (chances != null) {
                mobManager.getEliteChances().clear();
                mobManager.getEliteChances().putAll(chances);
            }
        }

        org.yinwu.config.DoomConfig doomConfig = configManager.getDoomConfig();
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
        if (bossBarManager.getBossBars().isEmpty()) {
            return; // 无活跃自定义袭击，放行原版袭击
        }
        // 只取消与活跃自定义袭击区域重叠的原版袭击，避免全局误伤
        Location raidLoc = event.getRaid().getLocation();
        if (raidLoc == null || raidLoc.getWorld() == null) return;
        double proximity = cachedVillageRadius + 32.0;
        double proximitySq = proximity * proximity;
        // 仅以「活跃袭击」的中心判定，避免历史信标残留误伤
        boolean nearCustomRaid = raidStatesByBeacon.values().stream()
            .filter(rs -> rs != null && rs.isActive)
            .map(rs -> rs.raidCenter)
            .anyMatch(loc -> loc != null
                && loc.getWorld() != null
                && loc.getWorld().equals(raidLoc.getWorld())
                && loc.distanceSquared(raidLoc) <= proximitySq);
        if (nearCustomRaid) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onRaidSpawnWave: 取消与自定义袭击重叠的原版袭击刷怪");
            }
            ((org.bukkit.event.Cancellable) event).setCancelled(true);
        }
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

    // ==================== Forge ↔ Raid 联动：锻造武器对袭击怪物额外伤害 ====================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamageRaidMob(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isRaidMob(event.getEntity())) return;
        // Folia：受害方区域线程，攻击者跨区域时读不到其背包，跳过加成（边界场景）
        if (!Bukkit.isOwnedByCurrentRegion(player)) return;

        if (!org.yinwu.util.ForgeBridge.isAvailable()) return; // Forge not installed

        var weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;
        if (!org.yinwu.util.ForgeBridge.isForgeItem(weapon)) return;

        int level = org.yinwu.util.ForgeBridge.getForgeLevel(weapon);
        if (level <= 0) return;

        // 每级锻造 +8% 伤害
        double bonus = event.getDamage() * (level * 0.08);
        event.setDamage(event.getDamage() + bonus);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().fine("§e[联动] 锻造武器+" + level + " 对袭击怪物额外 " + String.format("%.1f", bonus) + " 伤害");
        }
    }

    // ==================== Enchant ↔ Raid 联动：附魔对袭击怪物额外效果 ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchantVsRaidMob(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getDamager() instanceof Player player)) return;
        if (!isRaidMob(event.getEntity())) return;
        // Folia：受害方区域线程，攻击者跨区域时读不到其背包，跳过加成（边界场景）
        if (!Bukkit.isOwnedByCurrentRegion(player)) return;

        var weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || weapon.getType().isAir()) return;

        var customEnchants = org.yinwu.util.EnchantBridge.getEnchantments(weapon);
        if (customEnchants.isEmpty()) return;

        boolean isCreeper = event.getEntity().getType() == org.bukkit.entity.EntityType.CREEPER;

        for (var ce : customEnchants) {
            switch (ce.id()) {
                case "sonic_boom" -> {
                    // SonicBoom 对 raid mob 额外 +30% 伤害
                    double bonus = event.getDamage() * 0.30;
                    event.setDamage(event.getDamage() + bonus);
                    player.getWorld().playSound(player.getLocation(),
                        org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.2f);
                }
                case "cats_paw" -> {
                    // CatsPaw 对苦力怕额外伤害
                    if (isCreeper) {
                        event.setDamage(event.getDamage() * (1.0 + ce.level() * 0.25));
                    }
                }
                default -> {
                    // 其他自定义附魔：每级 +5% 伤害
                    double bonus = event.getDamage() * ce.level() * 0.05;
                    event.setDamage(event.getDamage() + bonus);
                }
            }
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().fine("§e[联动] 自定义附魔对袭击怪物额外伤害，最终伤害=" +
                String.format("%.1f", event.getDamage()));
        }
    }

    private boolean isRaidMob(Entity entity) {
        return raidStates.values().stream()
            .anyMatch(s -> s.raidMobs.contains(entity.getUniqueId()));
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
        UUID pUid = player.getUniqueId();

        if (beaconLocations.containsKey(pUid)) {
            beaconLoc = beaconLocations.get(pUid);
        } else if (plugin.getBeaconDetector() != null) {
            beaconLoc = plugin.getBeaconDetector().getLastDetectedBeacon();
            if (beaconLoc != null) {
                beaconLocations.put(pUid, beaconLoc.clone());
            }
        }

        if (beaconLoc != null) {
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
        Location centerSnapshot = center.clone();

        // 逐玩家在各自实体线程发送（规避跨区域读 getLocation）
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            final Player targetPlayer = onlinePlayer;
            targetPlayer.getScheduler().run(plugin, (task) -> {
                if (targetPlayer.isOnline() && targetPlayer.getWorld() == centerSnapshot.getWorld()) {
                    sendRaidActionBar(targetPlayer, broadcastMessage);
                }
            }, null);
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
                plugin.getLogger().fine("§e⚠ 设置袭击之兆效果失败: " + e.getMessage());
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
                // Rule 6：世界音效必须路由到信标区域线程
                Bukkit.getRegionScheduler().run(plugin, center, (s) ->
                    center.getWorld().playSound(center, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f));
            }

            if (secondsLeft[0] <= 0) {
                taskRef[0].cancel();
                countdownBar.setVisible(false);

                // Rule 6：世界音效必须路由到信标区域线程
                Bukkit.getRegionScheduler().run(plugin, center, (s) ->
                    center.getWorld().playSound(center, org.bukkit.Sound.ITEM_GOAT_HORN_SOUND_1, 1.0f, 1.0f));
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

        // Rule 6：ActionBar 属玩家操作，必须派发到玩家线程（调用方可能处于全局/其他区域线程）
        player.getScheduler().run(plugin, (task) -> {
            if (!player.isOnline()) return;
            try {
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));

                player.getScheduler().runDelayed(plugin, (clearTask) -> {
                    if (player.isOnline()) {
                        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(""));
                    }
                }, null, 60L);
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 发送 ActionBar 失败：" + e.getMessage());
            }
        }, null);
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
        raidStatesByBeacon.put(center.clone(), raidState); // 填充信标位置索引，供 getRaidStatesByBeacon 使用

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

    /** R12：战斗增益——按配置周期为袭击范围内玩家施加力量/速度（逐玩家实体线程，规避跨区域读位置） */
    private void applyCombatBuff(Location center, int doomLevel, long tickCounter) {
        org.yinwu.config.CombatBuffConfig combatBuff = configManager.getCombatBuffConfig();
        if (combatBuff == null || !combatBuff.isEnabled()) return;
        int buffEvery = Math.max(1, (int) (buffApplyInterval / Math.max(1L, raidSchedulerInterval)));
        if (tickCounter % buffEvery != 0) return;

        int buffRange = combatBuff.getRange();
        double rangeSq = (double) buffRange * buffRange;
        int buffDurationTicks = Math.max(20, combatBuff.getDuration() * 20);
        int amplifier = Math.min(2, Math.max(0, doomLevel - 6));
        Location centerSnapshot = center.clone();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            final Player p = onlinePlayer;
            p.getScheduler().run(plugin, (t) -> {
                if (p.isOnline() && p.getWorld() == centerSnapshot.getWorld()
                        && p.getLocation().distanceSquared(centerSnapshot) <= rangeSq) {
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.STRENGTH, buffDurationTicks, amplifier, false, true, true));
                    p.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.SPEED, buffDurationTicks, amplifier, false, true, true));
                }
            }, null);
        }
    }

    private void startRaidScheduler(Player player, Location center, int doomLevel, int radius,
                                    RaidState raidState, BossBar bossBar) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] startRaidScheduler: 玩家=" + player.getName() + ", 灾厄等级=" + doomLevel + ", 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 总波次=" + raidState.totalWaves + ", 每波怪物数=" + raidState.mobsPerWave);
        }

        // 彩蛋级灾厄：只刷史莱姆和苦力怕（兜底，不依赖配置）
        List<String> mobTypes = doomLevel == EASTER_EGG_LEVEL
            ? List.of("SLIME", "CREEPER")
            : mobManager.getRaidMobs().get(doomLevel);

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
        final int[] lastAliveMobs = {0};

        io.papermc.paper.threadedregions.scheduler.ScheduledTask raidTask =
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

            // R9 卡死检测：当前波存活数持续无进展（无死亡）超时判定失败
            int aliveNow = raidState.aliveMobs.get();
            if (aliveNow > 0) {
                if (aliveNow >= lastAliveMobs[0]) {
                    raidState.stalledTicks += raidSchedulerInterval;
                    if (raidState.stalledTicks >= RaidState.MAX_STALLED_TICKS) {
                        plugin.getLogger().warning("§c✗ 袭击卡死（怪物长时间无法消灭）超时，判定失败");
                        endRaidWithFailure(player, bossBar, raidState);
                        return;
                    }
                } else {
                    raidState.stalledTicks = 0;
                }
            } else {
                raidState.stalledTicks = 0;
            }
            lastAliveMobs[0] = aliveNow;

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

            // 更新 BossBar（本任务每 raidSchedulerInterval≈3s 执行一次，直接实时刷新）
            // 修复：原先 tickCounter % bossBarUpdateInterval 中 tickCounter 每任务才 +1，需 120s 才刷新一次，导致 BossBar 卡死在初始值
            bossBarManager.updateBossBarForWave(bossBar, raidState, doomLevel);

            // R12：战斗增益——周期为袭击范围内玩家施加力量/速度
            applyCombatBuff(center, doomLevel, tickCounter[0]);

            // 弥合已卸载/消失的怪（重进后村庄区块卸载会导致死亡事件不触发）
            reconcileRaidMobs(raidState);

            // 检查是否可以开始下一波
            if (raidState.currentWave < raidState.totalWaves) {
                if (raidState.currentWave > 0 && currentTime - raidState.lastSpawnTime < raidState.waveDelay * 50) {
                    return;
                }

                // 仅当本袭击的存活怪物归零才推进（避免并发袭击互相干扰）
                if (raidState.currentWave > 0 && raidState.aliveMobs.get() > 0) {
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

                    for (Player targetPlayer : Bukkit.getOnlinePlayers()) {
                        if (checkedCount++ > maxPlayerCheckCount) break;
                        final Player tp = targetPlayer;
                        tp.getScheduler().run(plugin, (playerTask) -> {
                            if (tp.isOnline() && tp.getWorld() == finalCenter.getWorld()
                                    && tp.getLocation().distance(finalCenter) <= finalRadius * 2) {
                                sendRaidActionBar(tp,
                                    String.format("§4§l\u274C 第 %d 波袭击 §r§7 - §e准备迎击 %d 只怪物！",
                                        currentWaveNum, mobsPerWaveCount));
                            }
                        }, null);
                    }

                    Bukkit.getRegionScheduler().run(plugin, center, (spawnTask) -> {
                        mobManager.spawnWaveMobs(center, doomLevel, radius, mobTypes, raidState);
                    });
                });

                return;
            } else {
                if (raidState.aliveMobs.get() <= 0) {
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

        raidSchedulerTasks.put(player.getUniqueId(), raidTask);

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
            // 遍历实际填充的 raidStates（raidStatesByBeacon 从未填充，此前此检测恒失效）
            for (RaidState raidState : raidStates.values()) {
                if (!raidState.isActive) continue;

                Location beaconLoc = raidState.raidCenter;
                if (beaconLoc == null || beaconLoc.getWorld() == null) continue;

                // 在袭击中心区域线程中扫描村民数量
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
                        // 找到对应玩家并结束袭击（离线玩家跳过，重进时调度器会重新检测）
                        Player player = Bukkit.getPlayer(raidState.playerId);
                        BossBar bossBar = bossBarManager.getBossBars().get(raidState.playerId);
                                if (player != null && bossBar != null) {
                                    plugin.getLogger().warning("§c\u2717 村庄内所有村民已死亡！立即结束袭击！");
                                    Bukkit.getGlobalRegionScheduler().run(plugin, (failTask) -> {
                                        endRaidWithFailure(player, bossBar, raidState);
                                    });
                                }
                    }
                });
            }
        }, 1L, 20L); // ✅ 初始延迟1L（Folia要求≥1），每20tick（1秒）扫描一次
    }


    // ==================== 结束袭击 ====================

    private void endRaid(Player player, BossBar bossBar, RaidState raidState) {
        // 防重入：成功/失败路径可能并发触发，已结束的袭击直接返回
        if (!raidState.isActive) return;
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

        // 取消袭击调度器任务
        io.papermc.paper.threadedregions.scheduler.ScheduledTask raidTask =
            raidSchedulerTasks.remove(player.getUniqueId());
        if (raidTask != null && !raidTask.isCancelled()) {
            raidTask.cancel();
        }

        // 清理迷雾效果
        fogEffectManager.stopWhiteFogEffect(player.getUniqueId());
        fogEffectManager.stopGreenFogEffect(player.getUniqueId());

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> {
            bossBarManager.removeBossBar(bossBar);
            bossBarManager.getBossBars().remove(player.getUniqueId());
            raidStates.remove(player.getUniqueId());
            raidStatesByBeacon.remove(raidState.raidCenter);

            Location beaconLoc = beaconLocations.get(player.getUniqueId());

            // 仅清理本袭击的怪物/缓存（避免清空其他并发袭击）
            cleanupRaidMobs(raidState);
            mobManager.getVillagerCountCache().clear();


            // ✅ 断链#1：通关奖励（村庄英雄 + 战利品），路由到信标/玩家区域
            final int doomLevel = raidState.originalDoomLevel;
            if (beaconLoc != null) {
                final Location rewardCenter = beaconLoc;
                Bukkit.getRegionScheduler().run(plugin, rewardCenter, (regionTask) -> {
                    giveHeroOfTheVillageToAllNearbyPlayers(rewardCenter, player, doomLevel);
                });
            } else {
                player.getScheduler().run(plugin, (playerTask) -> {
                    if (!player.isOnline()) return;
                    giveHeroOfTheVillageToAllNearbyPlayers(player.getLocation(), player, doomLevel);
                }, null);
            }

            // 袭击已结束：玩家离线则清理信标位置（防永久泄漏；在线玩家下次激活会覆盖）
            if (Bukkit.getPlayer(player.getUniqueId()) == null) {
                beaconLocations.remove(player.getUniqueId());
            }
        }, 60L);
    }

    private void endRaidWithFailure(Player player, BossBar bossBar, RaidState raidState) {
        // 防重入：成功/失败路径可能并发触发，已结束的袭击直接返回
        if (!raidState.isActive) return;
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

        // 取消袭击调度器任务
        io.papermc.paper.threadedregions.scheduler.ScheduledTask raidTask =
            raidSchedulerTasks.remove(player.getUniqueId());
        if (raidTask != null && !raidTask.isCancelled()) {
            raidTask.cancel();
        }

        fogEffectManager.stopWhiteFogEffect(player.getUniqueId());
        fogEffectManager.stopGreenFogEffect(player.getUniqueId());

        // Rule 6：showTitle 必须派发到玩家线程
        player.getScheduler().run(plugin, (t) -> {
            if (!player.isOnline()) return;
            player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("§c§l村民已全部死于灾厄"),
                Component.text("§7"),
                net.kyori.adventure.title.Title.Times.times(
                    java.time.Duration.ofMillis(titleFailFirstFadeIn * 50L),
                    java.time.Duration.ofMillis(titleFailFirstDisplay * 50L),
                    java.time.Duration.ofMillis(titleFailFirstFadeOut * 50L)
                )
            ));
        }, null);

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (t) -> {
            if (!player.isOnline()) {
                // 袭击已失败且玩家离线：清理信标位置，防止永久泄漏
                beaconLocations.remove(player.getUniqueId());
                return;
            }

            bossBarManager.removeBossBar(bossBar);
            bossBarManager.getBossBars().remove(player.getUniqueId());
            raidStates.remove(player.getUniqueId());
            raidStatesByBeacon.remove(raidState.raidCenter);

            Location beaconLoc = beaconLocations.get(player.getUniqueId());

            // 仅清理本袭击的怪物/缓存（避免清空其他并发袭击）
            cleanupRaidMobs(raidState);
            mobManager.getVillagerCountCache().clear();


            plugin.getLogger().warning("§c✗ 灾厄袭击失败 - 村庄内所有村民已死亡！");

            player.getScheduler().run(plugin, (t2) -> {
                if (!player.isOnline()) return;
                player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("§c§l我们失败了"),
                    Component.text("§7村庄已被灾厄笼罩\n§e灾厄力量正在蔓延..."),
                    net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(titleFailSecondFadeIn * 50L),
                        java.time.Duration.ofMillis(titleFailSecondDisplay * 50L),
                        java.time.Duration.ofMillis(titleFailSecondFadeOut * 50L)
                    )
                ));
            }, null);

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
        org.yinwu.config.RaidConfig raidConfig = configManager.getRaidConfig();
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

    /**
     * 通关奖励：为信标范围内所有玩家施加村庄英雄效果，触发者额外获得战利品。
     * 必须在 centerLocation 的区域线程调用。
     */
    private void giveHeroOfTheVillageToAllNearbyPlayers(Location centerLocation, Player triggerPlayer, int doomLevel) {
        org.yinwu.config.BeaconConfig beaconConfig = configManager.getBeaconConfig();
        int range = beaconConfig != null ? beaconConfig.getMaxRange() : 50;

        int amplifier = doomLevel - 1;
        org.yinwu.config.RaidConfig raidConfig = configManager.getRaidConfig();
        int durationSeconds = raidConfig != null ? raidConfig.getHeroEffectDuration() : 600;
        int duration = durationSeconds * 20;
        String romanNumeral = bossBarManager.getRomanNumeral(amplifier + 1);
        double rangeSq = (double) range * range;
        Location center = centerLocation.clone();

        String message = String.format("§b§l【村庄英雄 %s】§r§e%s §r§7成功击退了 %d 级灾厄袭击，为附近的玩家带来了村庄英雄 %s 效果！",
            romanNumeral, triggerPlayer.getName(), doomLevel, romanNumeral);

        // 逐玩家在各自实体线程校验距离并施加效果（规避跨区域读 getLocation）
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            final Player recipient = onlinePlayer;
            recipient.getScheduler().run(plugin, (task) -> {
                if (!recipient.isOnline() || recipient.getWorld() != center.getWorld()) return;

                if (recipient.getLocation().distanceSquared(center) <= rangeSq) {
                    recipient.removePotionEffect(org.bukkit.potion.PotionEffectType.BAD_OMEN);
                    recipient.removePotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN);

                    recipient.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE, duration, amplifier, false, true, true));

                    recipient.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(
                        "§b§l村庄英雄 " + romanNumeral + " §r§e - 你们成功保卫了村庄！§7（3 分钟）"));

                    // 触发者获得战利品（giveRaidLoot 内部自行调度区域线程）；用 UUID 比较避免重进后 Player 实例变化
                    if (recipient.getUniqueId().equals(triggerPlayer.getUniqueId())) {
                        lootManager.giveRaidLoot(recipient, doomLevel);
                    }
                } else {
                    sendRaidActionBar(recipient, message);
                }
            }, null);
        }
    }

    /**
     * 仅清理本袭击登记的怪物 UUID（防止 endRaid/失败时清空其他并发袭击的追踪）
     */
    private void cleanupRaidMobs(RaidState raidState) {
        for (UUID id : raidState.raidMobs) {
            mobManager.getActiveRaidMobs().remove(id);
            mobManager.getMobSearchOffset().remove(id);
            mobManager.getLastTargetSearchTime().remove(id);
            mobManager.getCreeperCheckOffset().remove(id);
            mobManager.getCachedFollowRanges().remove(id);
        }
        raidState.raidMobs.clear();
    }

    /**
     * 弥合被卸载/消失的怪：从存活计数剔除（村庄区块卸载后死亡事件不触发，防止卡波次）。
     * 在全局调度器线程调用。
     */
    private void reconcileRaidMobs(RaidState raidState) {
        if (raidState.raidMobs.isEmpty()) return;
        for (UUID id : raidState.raidMobs) {
            Entity e = Bukkit.getEntity(id);
            if (e == null) {
                if (raidState.raidMobs.remove(id)) {
                    raidState.aliveMobs.decrementAndGet();
                }
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

            // 只维护本袭击的存活计数（波次推进由 raidScheduler 统一负责，避免双推进/双奖励）
            for (RaidState rs : raidStates.values()) {
                if (rs.isActive && rs.raidMobs.remove(entityId)) {
                    rs.aliveMobs.decrementAndGet();
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
            // 断链#5：重进时重启袭击调度器（退出时已取消固定周期任务）
            if (!raidSchedulerTasks.containsKey(playerUuid)) {
                startRaidScheduler(player, raidState.raidCenter, raidState.originalDoomLevel,
                    cachedVillageRadius, raidState, bossBar);
            }
            plugin.getLogger().info(String.format("§a\u2713 玩家 %s 重新加入，恢复灾厄袭击", player.getName()));

            Bukkit.getGlobalRegionScheduler().run(plugin, (task) -> {
                bossBar.addPlayer(player);
                bossBar.setVisible(true);
                plugin.getLogger().fine(String.format("§a\u2713 已恢复玩家 %s 的 BossBar 显示", player.getName()));
                bossBarManager.updateBossBarForWave(bossBar, raidState, raidState.originalDoomLevel);
            });

            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                if (player.isOnline()) {
                    // Chat 消息持久可见（ActionBar 会被后续袭击提示覆盖且 3s 后清空）
                    player.sendMessage("§6【灾厄袭击】§e你还有未完成的灾厄袭击！");
                    player.sendMessage(String.format("§e 当前波次：%d/%d §7| §e剩余怪物：%d",
                        raidState.currentWave, raidState.totalWaves, raidState.aliveMobs.get()));
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        // 不 remove：onPlayerJoin 靠 bossBar != null 判断是否有未完成袭击，移除了则重进永远不恢复
        BossBar bar = bossBarManager.getBossBars().get(uid);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }

        // 断链#5：取消该玩家袭击的任务（避免孤立全局任务泄漏），但保留 raidState 供重进恢复
        io.papermc.paper.threadedregions.scheduler.ScheduledTask raidTask = raidSchedulerTasks.remove(uid);
        if (raidTask != null && !raidTask.isCancelled()) {
            raidTask.cancel();
        }
        io.papermc.paper.threadedregions.scheduler.ScheduledTask preSearchTask = preSearchTasks.remove(uid);
        if (preSearchTask != null) {
            preSearchTask.cancel();
        }
        io.papermc.paper.threadedregions.scheduler.ScheduledTask golemTask = defenderManager.getGolemSpawnTasks().remove(uid);
        if (golemTask != null) {
            golemTask.cancel();
        }
        fogEffectManager.stopWhiteFogEffect(uid);
        fogEffectManager.stopGreenFogEffect(uid);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        Entity entity = event.getEntity();

        if (entity instanceof Slime) {
            Slime slime = (Slime) entity;
            Location spawnLoc = entity.getLocation();

            Collection<Entity> nearby = spawnLoc.getWorld().getNearbyEntities(
                spawnLoc, 2, 2, 2,
                e -> mobManager.getActiveRaidMobs().contains(e.getUniqueId())
            );

            if (!nearby.isEmpty()) {
                if (configManager.isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [SpecialRaidListener] onEntitySpawn: 灾厄史莱姆分裂产生小史莱姆，已加入袭击列表");
                }
                mobManager.getActiveRaidMobs().add(entity.getUniqueId());

                // R23：将分裂小史莱姆计入所属袭击的存活计数，防止永久滞留/波次无法结束
                for (Entity parent : nearby) {
                    UUID parentId = parent.getUniqueId();
                    RaidState ownerRaid = raidStates.values().stream()
                        .filter(rs -> rs.raidMobs.contains(parentId))
                        .findFirst().orElse(null);
                    if (ownerRaid != null) {
                        ownerRaid.raidMobs.add(entity.getUniqueId());
                        ownerRaid.aliveMobs.incrementAndGet();
                        break;
                    }
                }

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

    public Map<UUID, Location> getBeaconLocations() { return beaconLocations; }
    public Map<UUID, RaidState> getRaidStates() { return raidStates; }
    public RaidLootManager getLootManager() { return lootManager; }
    public RaidDefenderManager getDefenderManager() { return defenderManager; }
    public Map<Location, RaidState> getRaidStatesByBeacon() { return raidStatesByBeacon; }

    public Location getBeaconLocation(UUID playerId) { return beaconLocations.get(playerId); }
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
