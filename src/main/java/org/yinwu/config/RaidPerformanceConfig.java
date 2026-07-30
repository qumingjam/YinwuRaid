package org.yinwu.config;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class RaidPerformanceConfig {
    private long villagerCheckInterval;
    private long bossBarUpdateInterval;
    private long fogEffectInterval;
    private int fogParticleCount;
    private long buffApplyInterval;
    private long raidSchedulerInterval;
    private int maxPlayerCheckCount;
    private Map<String, Double> entityFollowRange;
    private TitleTimingConfig titleTiming;

    public long getVillagerCheckInterval() { return villagerCheckInterval; }
    public void setVillagerCheckInterval(long villagerCheckInterval) { this.villagerCheckInterval = villagerCheckInterval; }
    public long getBossBarUpdateInterval() { return bossBarUpdateInterval; }
    public void setBossBarUpdateInterval(long bossBarUpdateInterval) { this.bossBarUpdateInterval = bossBarUpdateInterval; }
    public long getFogEffectInterval() { return fogEffectInterval; }
    public void setFogEffectInterval(long fogEffectInterval) { this.fogEffectInterval = fogEffectInterval; }
    public int getFogParticleCount() { return fogParticleCount; }
    public void setFogParticleCount(int fogParticleCount) { this.fogParticleCount = fogParticleCount; }
    public long getBuffApplyInterval() { return buffApplyInterval; }
    public void setBuffApplyInterval(long buffApplyInterval) { this.buffApplyInterval = buffApplyInterval; }
    public long getRaidSchedulerInterval() { return raidSchedulerInterval; }
    public void setRaidSchedulerInterval(long raidSchedulerInterval) { this.raidSchedulerInterval = raidSchedulerInterval; }
    public int getMaxPlayerCheckCount() { return maxPlayerCheckCount; }
    public void setMaxPlayerCheckCount(int maxPlayerCheckCount) { this.maxPlayerCheckCount = maxPlayerCheckCount; }
    public Map<String, Double> getEntityFollowRange() { return entityFollowRange; }
    public void setEntityFollowRange(Map<String, Double> entityFollowRange) { this.entityFollowRange = entityFollowRange; }
    public TitleTimingConfig getTitleTiming() { return titleTiming; }
    public void setTitleTiming(TitleTimingConfig titleTiming) { this.titleTiming = titleTiming; }

    /** 从 config.yml raid-performance 段加载 */
    public static RaidPerformanceConfig from(ConfigurationSection s) {
        RaidPerformanceConfig c = new RaidPerformanceConfig();
        if (s == null) {
            c.setVillagerCheckInterval(60); c.setBossBarUpdateInterval(20);
            c.setFogEffectInterval(10); c.setFogParticleCount(30);
            c.setBuffApplyInterval(200); c.setRaidSchedulerInterval(60);
            c.setMaxPlayerCheckCount(50);
            return c;
        }
        c.setVillagerCheckInterval(s.getLong("villager-check-interval", 60));
        c.setBossBarUpdateInterval(s.getLong("bossbar-update-interval", 20));
        c.setFogEffectInterval(s.getLong("fog-effect-interval", 10));
        c.setFogParticleCount(s.getInt("fog-particle-count", 30));
        c.setBuffApplyInterval(s.getLong("buff-apply-interval", 200));
        c.setRaidSchedulerInterval(s.getLong("raid-scheduler-interval", 60));
        c.setMaxPlayerCheckCount(s.getInt("max-player-check-count", 50));

        ConfigurationSection ef = s.getConfigurationSection("entity-follow-range");
        if (ef != null) {
            Map<String, Double> m = new HashMap<>();
            for (String k : ef.getKeys(false)) m.put(k, ef.getDouble(k, 48.0));
            c.setEntityFollowRange(m);
        }

        ConfigurationSection tt = s.getConfigurationSection("title-timing");
        if (tt != null) {
            ConfigurationSection fail = tt.getConfigurationSection("failure");
            if (fail != null) c.setTitleTiming(TitleTimingConfig.from(fail));
        }
        return c;
    }
}
