package org.yinwu.raid;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.Collection;

public class RaidBuffManager {
    private final YinwuRaidPlugin plugin;
    private final SpecialRaidListener listener;
    private final ConfigManager configManager;

    public RaidBuffManager(YinwuRaidPlugin plugin, SpecialRaidListener listener) {
        this.plugin = plugin; this.listener = listener;
        this.configManager = plugin.getConfigManager();
    }

    public void giveHeroOfTheVillageToAllNearbyPlayers(Location center, int doomLevel) {
        int radius = configManager.getRaidConfig() != null ? configManager.getRaidConfig().getVillageRadius() : 48;
        int duration = configManager.getRaidConfig() != null ? configManager.getRaidConfig().getHeroEffectDuration() : 600;
        Collection<org.bukkit.entity.Entity> nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius,
            e -> e instanceof Player);
        for (var e : nearby) {
            Player p = (Player) e;
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, duration * 20, doomLevel - 6));
        }
    }
}
