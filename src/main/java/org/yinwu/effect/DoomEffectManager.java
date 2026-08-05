package org.yinwu.effect;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.yinwu.YinwuRaidPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DoomEffectManager {

    private final YinwuRaidPlugin plugin;
    private final PotionEffectType doomEffectType;
    private final PotionEffectType raidOmenType;

    /** 有灾厄效果的玩家缓存，cleanup 时只遍历这里 */
    private final Set<UUID> affectedPlayers = ConcurrentHashMap.newKeySet();

    public DoomEffectManager(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        this.doomEffectType = PotionEffectType.BAD_OMEN;
        this.raidOmenType = PotionEffectType.RAID_OMEN;
    }

    public void applyDoomEffect(Player player, int beaconLevel, int duration) {
        var configManager = plugin.getConfigManager();
        var beaconConfig = configManager.getBeaconConfig();
        int doomLevel = beaconConfig.getDoomLevels().getOrDefault(beaconLevel, beaconLevel + 6);

        affectedPlayers.add(player.getUniqueId());

        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;

            player.removePotionEffect(doomEffectType);
            player.removePotionEffect(raidOmenType);

            player.addPotionEffect(new PotionEffect(
                doomEffectType, duration, doomLevel - 1, false, true, true
            ));
        });
    }

    public boolean hasDoomEffect(Player player) {
        return affectedPlayers.contains(player.getUniqueId());
    }

    public int getDoomLevel(Player player) {
        PotionEffect effect = player.getPotionEffect(raidOmenType);
        if (effect != null) return effect.getAmplifier() + 1;
        effect = player.getPotionEffect(doomEffectType);
        if (effect != null) return effect.getAmplifier() + 1;
        return 0;
    }

    public void removeDoomEffect(Player player) {
        affectedPlayers.remove(player.getUniqueId());
        if (doomEffectType != null) player.removePotionEffect(doomEffectType);
    }

    public void cleanup() {
        for (UUID uuid : affectedPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                try {
                    // Rule 6：药水操作须在玩家线程（插件禁用时调度可能不执行，属尽力清理）
                    player.getScheduler().run(plugin, (task) -> {
                        if (player.isOnline()) {
                            player.removePotionEffect(doomEffectType);
                        }
                    }, null);
                } catch (Exception ignored) {}
            }
        }
        affectedPlayers.clear();
    }
}
