package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class PerformanceConfig {
    private boolean asyncTasks;
    private int entitySpawnLimit;
    private int particleLimit;
    private boolean cacheEnabled;
    private long cacheExpiry;

    public boolean isAsyncTasks() { return asyncTasks; }
    public void setAsyncTasks(boolean asyncTasks) { this.asyncTasks = asyncTasks; }
    public int getEntitySpawnLimit() { return entitySpawnLimit; }
    public void setEntitySpawnLimit(int entitySpawnLimit) { this.entitySpawnLimit = entitySpawnLimit; }
    public int getParticleLimit() { return particleLimit; }
    public void setParticleLimit(int particleLimit) { this.particleLimit = particleLimit; }
    public boolean isCacheEnabled() { return cacheEnabled; }
    public void setCacheEnabled(boolean cacheEnabled) { this.cacheEnabled = cacheEnabled; }
    public long getCacheExpiry() { return cacheExpiry; }
    public void setCacheExpiry(long cacheExpiry) { this.cacheExpiry = cacheExpiry; }

    /** 从配置段加载 */
    public static PerformanceConfig from(ConfigurationSection s) {
        PerformanceConfig c = new PerformanceConfig();
        if (s == null) return c;
        c.setAsyncTasks(s.getBoolean("async-tasks", true));
        c.setEntitySpawnLimit(s.getInt("entity-spawn-limit", 50));
        c.setParticleLimit(s.getInt("particle-limit", 100));
        c.setCacheEnabled(s.getBoolean("cache-enabled", true));
        c.setCacheExpiry(s.getLong("cache-expiry", 300));
        return c;
    }
}
