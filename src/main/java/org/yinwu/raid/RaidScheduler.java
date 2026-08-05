package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RaidScheduler {
    private final YinwuRaidPlugin plugin;
    private final SpecialRaidListener listener;
    private final ConfigManager configManager;

    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    /** 灾厄效果检测任务 — 全局唯一，去重后复用，插件禁用时取消 */
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask doomDetectionTask;

    public RaidScheduler(YinwuRaidPlugin plugin, SpecialRaidListener listener) {
        this.plugin = plugin; this.listener = listener;
        this.configManager = plugin.getConfigManager();
    }

    public void startDoomDetectionTask() {
        // 去重：已有活跃任务则不重复启动（每次右键信标都会调用 setBeaconLocation）
        if (doomDetectionTask != null && !doomDetectionTask.isCancelled()) {
            return;
        }
        doomDetectionTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> {
                    int level = 0;
                    if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN)) {
                        level = player.getPotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN).getAmplifier() + 1;
                    } else if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BAD_OMEN)) {
                        // 信标激活给的是 BAD_OMEN（灾厄效果）。原版 RaidTriggerEvent 对高等级(>5)不触发，这里兜底
                        level = player.getPotionEffect(org.bukkit.potion.PotionEffectType.BAD_OMEN).getAmplifier() + 1;
                    }
                    if (level >= 6 && level <= 10 && !isOnCooldown(player.getUniqueId())) {
                        setCooldown(player.getUniqueId());
                        listener.triggerSpecialRaidPublic(player, level);
                    }
                }, null);
            }
            playerCooldowns.entrySet().removeIf(e -> System.currentTimeMillis() >= e.getValue());
        }, 1L, 60L);
    }

    /** 取消灾厄检测任务（插件禁用时调用） */
    public void cleanup() {
        if (doomDetectionTask != null && !doomDetectionTask.isCancelled()) {
            doomDetectionTask.cancel();
        }
        doomDetectionTask = null;
        playerCooldowns.clear();
    }

    public boolean isOnCooldown(UUID uuid) {
        Long expiry = playerCooldowns.get(uuid);
        return expiry != null && System.currentTimeMillis() < expiry;
    }

    public void setCooldown(UUID uuid) {
        long cooldown = configManager.getRaidConfig() != null ? configManager.getRaidConfig().getCooldown() : 3600;
        playerCooldowns.put(uuid, System.currentTimeMillis() + cooldown * 1000);
    }
}
