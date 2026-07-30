package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class DebugConfig {
    private boolean enabled;
    private boolean creeperDetection;
    private boolean spawnLocation;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCreeperDetection() { return creeperDetection; }
    public void setCreeperDetection(boolean creeperDetection) { this.creeperDetection = creeperDetection; }
    public boolean isSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(boolean spawnLocation) { this.spawnLocation = spawnLocation; }

    /** 从配置段加载 */
    public static DebugConfig from(ConfigurationSection s) {
        DebugConfig c = new DebugConfig();
        if (s == null) return c;
        c.setEnabled(s.getBoolean("enabled", false));
        c.setCreeperDetection(s.getBoolean("creeper-detection", true));
        c.setSpawnLocation(s.getBoolean("spawn-location", false));
        return c;
    }
}
