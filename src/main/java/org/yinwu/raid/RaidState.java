package org.yinwu.raid;

/**
 * 灾厄袭击状态
 */
public class RaidState {
    // 当前波次
    public int currentWave = 0;
    // 总波次
    public int totalWaves;
    // 每波怪物数量
    public int mobsPerWave;
    // 本波已生成数量
    public int spawnedThisWave = 0;
    // 上次生成时间
    public long lastSpawnTime = 0;
    // 波次间隔（tick）- 从配置读取
    public long waveDelay;
    // 怪物生成间隔（tick）- 从配置读取
    public long mobInterval;
    // 是否活跃
    public boolean isActive = true;
    // 初始村民数量（用于检测失败）
    public int initialVillagerCount = 0;
    // 原始灾厄等级（胜利后直接使用，无需反推）
    public int originalDoomLevel;

    public RaidState(int totalWaves, int mobsPerWave, long waveDelay, long mobInterval, int doomLevel) {
        this.totalWaves = totalWaves;
        this.mobsPerWave = mobsPerWave;
        this.waveDelay = waveDelay;
        this.mobInterval = mobInterval;
        this.originalDoomLevel = doomLevel;
    }
}
