package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class RaidConfig {
    private boolean enabled;
    private long cooldown;
    private int detectionRadius;
    private boolean doomEffectEnabled;
    private int villageRadius;
    private int heroEffectDuration;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getCooldown() { return cooldown; }
    public void setCooldown(long cooldown) { this.cooldown = cooldown; }
    public int getDetectionRadius() { return detectionRadius; }
    public void setDetectionRadius(int detectionRadius) { this.detectionRadius = detectionRadius; }
    public boolean isDoomEffectEnabled() { return doomEffectEnabled; }
    public void setDoomEffectEnabled(boolean doomEffectEnabled) { this.doomEffectEnabled = doomEffectEnabled; }
    public int getVillageRadius() { return villageRadius; }
    public void setVillageRadius(int villageRadius) { this.villageRadius = villageRadius; }
    public int getHeroEffectDuration() { return heroEffectDuration; }
    public void setHeroEffectDuration(int heroEffectDuration) { this.heroEffectDuration = heroEffectDuration; }

    /** 从配置段加载 */
    public static RaidConfig from(ConfigurationSection s) {
        RaidConfig c = new RaidConfig();
        if (s == null) return c;
        c.setEnabled(s.getBoolean("enabled", true));
        c.setCooldown(s.getLong("cooldown", 3600));
        c.setDetectionRadius(s.getInt("detection-radius", 128));
        c.setDoomEffectEnabled(s.getBoolean("doom-effect-enabled", true));
        c.setVillageRadius(s.getInt("village-radius", 48));
        c.setHeroEffectDuration(s.getInt("hero-effect-duration", 600));
        return c;
    }
}
