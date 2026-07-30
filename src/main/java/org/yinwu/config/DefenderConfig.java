package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class DefenderConfig {
    private double healthMultiplier;
    private double damageMultiplier;
    private double speedMultiplier;

    public double getHealthMultiplier() { return healthMultiplier; }
    public void setHealthMultiplier(double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(double damageMultiplier) { this.damageMultiplier = damageMultiplier; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(double speedMultiplier) { this.speedMultiplier = speedMultiplier; }

    /** 从配置段加载 */
    public static DefenderConfig from(ConfigurationSection s) {
        if (s == null) return null;
        DefenderConfig c = new DefenderConfig();
        c.setHealthMultiplier(s.getDouble("health-multiplier", 3.0));
        c.setDamageMultiplier(s.getDouble("damage-multiplier", 2.0));
        c.setSpeedMultiplier(s.getDouble("speed-multiplier", 2.0));
        return c;
    }
}
