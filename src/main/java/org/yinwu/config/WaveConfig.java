package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class WaveConfig {
    private int waveOffset;
    private int easterEggWaves;
    private long waveDelay;
    private long mobInterval;

    public int getWaveOffset() { return waveOffset; }
    public void setWaveOffset(int waveOffset) { this.waveOffset = waveOffset; }
    public int getEasterEggWaves() { return easterEggWaves; }
    public void setEasterEggWaves(int easterEggWaves) { this.easterEggWaves = easterEggWaves; }
    public long getWaveDelay() { return waveDelay; }
    public void setWaveDelay(long waveDelay) { this.waveDelay = waveDelay; }
    public long getMobInterval() { return mobInterval; }
    public void setMobInterval(long mobInterval) { this.mobInterval = mobInterval; }

    /** 从配置段加载 */
    public static WaveConfig from(ConfigurationSection s) {
        WaveConfig c = new WaveConfig();
        if (s == null) return c;
        c.setWaveOffset(s.getInt("wave-offset", 5));
        c.setEasterEggWaves(s.getInt("easter-egg-waves", 11));
        c.setWaveDelay(s.getLong("wave-delay", 200));
        c.setMobInterval(s.getLong("mob-interval", 40));
        return c;
    }
}
