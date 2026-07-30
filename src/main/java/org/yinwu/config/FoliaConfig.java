package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class FoliaConfig {
    private boolean enabled;
    private boolean regionScheduler;
    private boolean entityThreadGroup;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isRegionScheduler() { return regionScheduler; }
    public void setRegionScheduler(boolean regionScheduler) { this.regionScheduler = regionScheduler; }
    public boolean isEntityThreadGroup() { return entityThreadGroup; }
    public void setEntityThreadGroup(boolean entityThreadGroup) { this.entityThreadGroup = entityThreadGroup; }

    /** 从配置段加载 */
    public static FoliaConfig from(ConfigurationSection s) {
        FoliaConfig c = new FoliaConfig();
        if (s == null) return c;
        c.setEnabled(s.getBoolean("enabled", true));
        c.setRegionScheduler(s.getBoolean("region-scheduler", true));
        c.setEntityThreadGroup(s.getBoolean("entity-thread-group", true));
        return c;
    }
}
