package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class BeaconDefenderConfig {
    private double scaleMultiplier;

    public double getScaleMultiplier() { return scaleMultiplier; }
    public void setScaleMultiplier(double scaleMultiplier) { this.scaleMultiplier = scaleMultiplier; }

    /** 从配置段加载 */
    public static BeaconDefenderConfig from(ConfigurationSection s) {
        BeaconDefenderConfig c = new BeaconDefenderConfig();
        if (s == null) return c;
        c.setScaleMultiplier(s.getDouble("scale-multiplier", 2.0));
        return c;
    }
}
