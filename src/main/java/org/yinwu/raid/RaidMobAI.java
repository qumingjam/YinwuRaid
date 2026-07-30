package org.yinwu.raid;

import org.bukkit.entity.LivingEntity;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

public class RaidMobAI {
    private final YinwuRaidPlugin plugin;
    private final RaidMobManager mobManager;
    private final SpecialRaidListener listener;
    private final ConfigManager configManager;

    public RaidMobAI(YinwuRaidPlugin plugin, RaidMobManager mobManager, SpecialRaidListener listener) {
        this.plugin = plugin; this.mobManager = mobManager;
        this.listener = listener; this.configManager = plugin.getConfigManager();
    }

    public void enhanceRaidMob(LivingEntity entity, int doomLevel) {
        double hpMult = 1.0 + (doomLevel - 6) * 0.3;
        double dmgMult = 1.0 + (doomLevel - 6) * 0.2;
        entity.setMaxHealth(entity.getMaxHealth() * hpMult);
        entity.setHealth(entity.getMaxHealth());
    }
}
