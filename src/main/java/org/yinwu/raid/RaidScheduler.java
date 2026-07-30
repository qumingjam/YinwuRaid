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

    public RaidScheduler(YinwuRaidPlugin plugin, SpecialRaidListener listener) {
        this.plugin = plugin; this.listener = listener;
        this.configManager = plugin.getConfigManager();
    }

    public void startDoomDetectionTask() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> {
                    if (player.hasPotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN)) {
                        int level = player.getPotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN).getAmplifier() + 1;
                        if (level >= 6 && level <= 10 && !isOnCooldown(player.getUniqueId())) {
                            setCooldown(player.getUniqueId());
                            listener.triggerSpecialRaidPublic(player, level);
                        }
                    }
                }, null);
            }
            playerCooldowns.entrySet().removeIf(e -> System.currentTimeMillis() >= e.getValue());
        }, 1L, 60L);
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
