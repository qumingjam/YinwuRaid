package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class VillagerGiftConfig {
    private double range;
    private int minCooldown;
    private int maxCooldown;
    private long detectionInterval;

    public double getRange() { return range; }
    public void setRange(double range) { this.range = range; }
    public int getMinCooldown() { return minCooldown; }
    public void setMinCooldown(int minCooldown) { this.minCooldown = minCooldown; }
    public int getMaxCooldown() { return maxCooldown; }
    public void setMaxCooldown(int maxCooldown) { this.maxCooldown = maxCooldown; }
    public long getDetectionInterval() { return detectionInterval; }
    public void setDetectionInterval(long detectionInterval) { this.detectionInterval = detectionInterval; }

    /** 从配置段加载 */
    public static VillagerGiftConfig from(ConfigurationSection s) {
        VillagerGiftConfig c = new VillagerGiftConfig();
        c.setRange(5.0);
        c.setMinCooldown(30);
        c.setMaxCooldown(180);
        c.setDetectionInterval(100);
        if (s == null) return c;
        c.setRange(s.getDouble("range", 5.0));
        c.setMinCooldown(s.getInt("min-cooldown", 30));
        c.setMaxCooldown(s.getInt("max-cooldown", 180));
        c.setDetectionInterval(s.getLong("detection-interval", 100));
        return c;
    }
}
