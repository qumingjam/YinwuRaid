package org.yinwu.config;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class EliteConfig {
    private Map<Integer, Double> chances;
    private double healthMultiplier;
    private double damageMultiplier;
    private double scaleMultiplier;
    private boolean bossEnabled = true;
    private String bossType = "WARDEN";
    private double bossHealthMultiplier = 5.0;

    public Map<Integer, Double> getChances() { return chances; }
    public void setChances(Map<Integer, Double> chances) { this.chances = chances; }
    public double getHealthMultiplier() { return healthMultiplier; }
    public void setHealthMultiplier(double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(double damageMultiplier) { this.damageMultiplier = damageMultiplier; }
    public double getScaleMultiplier() { return scaleMultiplier; }
    public void setScaleMultiplier(double scaleMultiplier) { this.scaleMultiplier = scaleMultiplier; }
    public boolean isBossEnabled() { return bossEnabled; }
    public String getBossType() { return bossType; }
    public double getBossHealthMultiplier() { return bossHealthMultiplier; }

    /** 从 raid/config.yml elite-settings 段加载 */
    public static EliteConfig from(ConfigurationSection s) {
        EliteConfig c = new EliteConfig();
        if (s == null) {
            c.setHealthMultiplier(1.5); c.setDamageMultiplier(1.3); c.setScaleMultiplier(1.8);
            return c;
        }
        ConfigurationSection ch = s.getConfigurationSection("chances");
        if (ch != null) {
            Map<Integer, Double> map = new HashMap<>();
            for (String k : ch.getKeys(false)) {
                try {
                    int level = Integer.parseInt(k.replace("level-", ""));
                    map.put(level, ch.getDouble(k, 0.0));
                } catch (NumberFormatException ignored) {}
            }
            c.setChances(map);
        }
        ConfigurationSection bn = s.getConfigurationSection("bonuses");
        if (bn != null) {
            c.setHealthMultiplier(bn.getDouble("health-multiplier", 1.5));
            c.setDamageMultiplier(bn.getDouble("damage-multiplier", 1.3));
            c.setScaleMultiplier(bn.getDouble("scale-multiplier", 1.8));
        }
        ConfigurationSection boss = s.getConfigurationSection("boss");
        if (boss != null) {
            c.bossEnabled = boss.getBoolean("enabled", true);
            c.bossType = boss.getString("type", "WARDEN");
            c.bossHealthMultiplier = boss.getDouble("health-multiplier", 5.0);
        }
        return c;
    }
}
