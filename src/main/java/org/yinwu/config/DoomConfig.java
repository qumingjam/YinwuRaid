package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class DoomConfig {
    private double bonusPerLevel;

    public double getBonusPerLevel() { return bonusPerLevel; }
    public void setBonusPerLevel(double bonusPerLevel) { this.bonusPerLevel = bonusPerLevel; }

    /** 从配置段加载 */
    public static DoomConfig from(ConfigurationSection s) {
        DoomConfig c = new DoomConfig();
        if (s == null) return c;
        c.setBonusPerLevel(s.getDouble("bonus-per-level", 0.2));
        return c;
    }
}
