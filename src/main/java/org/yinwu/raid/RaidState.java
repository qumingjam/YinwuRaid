package org.yinwu.raid;

import org.bukkit.Location;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class RaidState {
    /** 跨区域读写（Folia 多线程），以下字段全部 volatile 保证可见性 */
    public volatile int currentWave = 0;
    public int totalWaves;
    public int mobsPerWave;
    public volatile int spawnedThisWave = 0;
    public volatile long lastSpawnTime = 0;
    public long waveDelay;
    public long mobInterval;
    public volatile boolean isActive = true;
    public volatile int initialVillagerCount = 0;
    public int originalDoomLevel;

    /** 当前波次存活怪物数 — AtomicInteger 跨区域线程安全 */
    public final AtomicInteger aliveMobs = new AtomicInteger(0);
    /** 本波实际生成怪物数（含每波 1 只幻术师），由 spawnWaveMobs 末尾写入，作 BossBar 剩余分母 */
    public volatile int waveMobCount = 0;
    /** 袭击中心位置（信标位置） */
    public final Location raidCenter;
    /** 卡死计数器（tick），无进展超时后判定失败 */
    public volatile int stalledTicks = 0;
    /** 最大卡死 tick 数（60秒 = 1200 tick） */
    public static final int MAX_STALLED_TICKS = 1200;
    /** 触发此袭击的玩家 UUID */
    public final UUID playerId;
    /** 属于此次袭击的怪物 UUID 集合 */
    public final java.util.Set<UUID> raidMobs = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public RaidState(int totalWaves, int mobsPerWave, long waveDelay, long mobInterval, int doomLevel, Location raidCenter, UUID playerId) {
        this.totalWaves = totalWaves;
        this.mobsPerWave = mobsPerWave;
        this.waveDelay = waveDelay;
        this.mobInterval = mobInterval;
        this.originalDoomLevel = doomLevel;
        this.raidCenter = raidCenter.clone();
        this.playerId = playerId;
    }
}
