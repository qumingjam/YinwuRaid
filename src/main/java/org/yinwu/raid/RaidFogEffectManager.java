package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灾厄袭击迷雾效果管理器
 * 负责白色迷雾（普通灾厄）和绿色迷雾（彩蛋级）效果
 */
public class RaidFogEffectManager {

    private final YinwuRaidPlugin plugin;
    private final ConfigManager configManager;

    // 白色迷雾效果任务（玩家 UUID -> ScheduledTask）
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> fogTasks = new ConcurrentHashMap<>();

    // 绿色迷雾效果任务（彩蛋级，玩家 UUID -> ScheduledTask）
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> greenFogTasks = new ConcurrentHashMap<>();

    // 缓存配置值
    private long fogEffectInterval = 10;
    private int fogParticleCount = 30;

    public RaidFogEffectManager(YinwuRaidPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // 加载性能配置
        org.yinwu.config.RaidPerformanceConfig perfConfig = configManager.getRaidPerformanceConfig();
        if (perfConfig != null) {
            fogEffectInterval = perfConfig.getFogEffectInterval();
            fogParticleCount = perfConfig.getFogParticleCount();
        }
    }

    /**
     * 清理所有迷雾效果
     */
    public void cleanup() {
        stopAllWhiteFogEffects();
        stopAllGreenFogEffects();
    }

    // ============== 白色迷雾效果 ==============

    /**
     * 启动白色迷雾效果（灾厄等级 7-10 级）
     */
    public void startWhiteFogEffect(Player player, Location center, int radius) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidFogEffectManager] startWhiteFogEffect: 玩家=" + player.getName() + ", 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 半径=" + radius);
        }
        UUID playerUuid = player.getUniqueId();
        stopWhiteFogEffect(playerUuid);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask fogTask =
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
                if (!player.isOnline()) {
                    task.cancel();
                    fogTasks.remove(playerUuid);
                    return;
                }

                Bukkit.getRegionScheduler().run(plugin, center, (regionTask) -> {
                    try {
                        Random random = ThreadLocalRandom.current();
                        int particleCount = fogParticleCount / 5;

                        for (int i = 0; i < particleCount; i++) {
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double distance = random.nextDouble() * radius;
                            int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
                            int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);
                            int y = center.getWorld().getHighestBlockYAt(x, z) + random.nextInt(5) + 1;

                            Location particleLoc = new Location(center.getWorld(), x + 0.5, y, z + 0.5);
                            center.getWorld().spawnParticle(
                                Particle.WHITE_ASH,
                                particleLoc,
                                1, 0.5, 0.5, 0.5, 0.01
                            );
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e\u26A0 生成迷雾粒子失败：" + e.getMessage());
                    }
                });
            }, 5L, fogEffectInterval);

        fogTasks.put(playerUuid, fogTask);
    }

    /**
     * 清除指定玩家的白色迷雾效果
     */
    public void stopWhiteFogEffect(UUID playerUuid) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask fogTask = fogTasks.remove(playerUuid);
        if (fogTask != null && !fogTask.isCancelled()) {
            fogTask.cancel();
        }
    }

    /**
     * 清除所有玩家的白色迷雾效果
     */
    public void stopAllWhiteFogEffects() {
        int count = fogTasks.size();
        for (UUID playerUuid : fogTasks.keySet()) {
            stopWhiteFogEffect(playerUuid);
        }
        fogTasks.clear();
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidFogEffectManager] stopAllWhiteFogEffects: 已清理 " + count + " 个白色迷雾效果");
        }
    }

    // ============== 绿色迷雾效果（彩蛋级） ==============

    /**
     * 启动绿色迷雾效果（彩蛋级 6 级）
     */
    public void startGreenFogEffect(Player player, Location center, int radius) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidFogEffectManager] startGreenFogEffect: 玩家=" + player.getName() + ", 中心=(" + center.getBlockX() + "," + center.getBlockY() + "," + center.getBlockZ() + "), 半径=" + radius);
        }
        UUID playerUuid = player.getUniqueId();
        stopGreenFogEffect(playerUuid);

        io.papermc.paper.threadedregions.scheduler.ScheduledTask greenFogTask =
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
                if (!player.isOnline()) {
                    task.cancel();
                    greenFogTasks.remove(playerUuid);
                    return;
                }

                Bukkit.getRegionScheduler().run(plugin, center, (regionTask) -> {
                    try {
                        Random random = ThreadLocalRandom.current();
                        int particleCount = fogParticleCount / 5;

                        for (int i = 0; i < particleCount; i++) {
                            double angle = random.nextDouble() * 2 * Math.PI;
                            double distance = random.nextDouble() * radius;
                            int x = center.getBlockX() + (int) (Math.cos(angle) * distance);
                            int z = center.getBlockZ() + (int) (Math.sin(angle) * distance);
                            int y = center.getWorld().getHighestBlockYAt(x, z) + random.nextInt(5) + 1;

                            Location particleLoc = new Location(center.getWorld(), x + 0.5, y, z + 0.5);
                            center.getWorld().spawnParticle(
                                Particle.CAMPFIRE_SIGNAL_SMOKE,
                                particleLoc,
                                1, 0.5, 0.5, 0.5, 0.01,
                                null
                            );
                        }
                    } catch (Exception e) {
                        plugin.getLogger().warning("§e\u26A0 生成绿色迷雾粒子失败：" + e.getMessage());
                    }
                });
            }, 5L, fogEffectInterval);

        greenFogTasks.put(playerUuid, greenFogTask);
    }

    /**
     * 清除指定玩家的绿色迷雾效果
     */
    public void stopGreenFogEffect(UUID playerUuid) {
        io.papermc.paper.threadedregions.scheduler.ScheduledTask greenFogTask = greenFogTasks.remove(playerUuid);
        if (greenFogTask != null && !greenFogTask.isCancelled()) {
            greenFogTask.cancel();
        }
    }

    /**
     * 清除所有玩家的绿色迷雾效果
     */
    public void stopAllGreenFogEffects() {
        int count = greenFogTasks.size();
        for (UUID playerUuid : greenFogTasks.keySet()) {
            stopGreenFogEffect(playerUuid);
        }
        greenFogTasks.clear();
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidFogEffectManager] stopAllGreenFogEffects: 已清理 " + count + " 个绿色迷雾效果");
        }
    }
}
