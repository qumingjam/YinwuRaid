package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class WaveConfig {
    private int waveOffset;
    private int easterEggWaves;
    private long waveDelay;
    private long mobInterval;
    /** 每波怪物数（按灾厄等级 6-10），缺省用默认表 */
    private Map<Integer, Integer> mobsPerWave = Map.of(6, 15, 7, 10, 8, 15, 9, 20, 10, 25);

    public int getWaveOffset() { return waveOffset; }
    public void setWaveOffset(int waveOffset) { this.waveOffset = waveOffset; }
    public int getEasterEggWaves() { return easterEggWaves; }
    public void setEasterEggWaves(int easterEggWaves) { this.easterEggWaves = easterEggWaves; }
    public long getWaveDelay() { return waveDelay; }
    public void setWaveDelay(long waveDelay) { this.waveDelay = waveDelay; }
    public long getMobInterval() { return mobInterval; }
    public void setMobInterval(long mobInterval) { this.mobInterval = mobInterval; }
    public int getMobsPerWave(int doomLevel) { return mobsPerWave.getOrDefault(doomLevel, 10); }

    /** 从配置段加载 */
    public static WaveConfig from(ConfigurationSection s) {
        WaveConfig c = new WaveConfig();
        if (s == null) return c;
        c.setWaveOffset(s.getInt("wave-offset", 5));
        c.setEasterEggWaves(s.getInt("easter-egg-waves", 11));
        c.setWaveDelay(s.getLong("wave-delay", 200));
        c.setMobInterval(s.getLong("mob-interval", 40));
        ConfigurationSection mpw = s.getConfigurationSection("mobs-per-wave");
        if (mpw != null) {
            Map<Integer, Integer> map = new HashMap<>();
            for (String k : mpw.getKeys(false)) {
                try { map.put(Integer.parseInt(k), mpw.getInt(k, 10)); }
                catch (NumberFormatException ignored) {}
            }
            if (!map.isEmpty()) c.mobsPerWave = map;
        }
        return c;
    }
}
