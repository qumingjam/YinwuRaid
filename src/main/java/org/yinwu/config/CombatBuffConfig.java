package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class CombatBuffConfig {
    private boolean enabled;
    private int range;
    private int duration;
    private long interval;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getRange() { return range; }
    public void setRange(int range) { this.range = range; }
    public int getDuration() { return duration; }
    public void setDuration(int duration) { this.duration = duration; }
    public long getInterval() { return interval; }
    public void setInterval(long interval) { this.interval = interval; }

    /** 从配置段加载 */
    public static CombatBuffConfig from(ConfigurationSection s) {
        CombatBuffConfig c = new CombatBuffConfig();
        if (s == null) return c;
        c.setEnabled(s.getBoolean("enabled", true));
        c.setRange(s.getInt("range", 32));
        c.setDuration(s.getInt("duration", 10));
        c.setInterval(s.getLong("interval", 200));
        return c;
    }
}
