package org.yinwu.raid;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Bukkit;
import org.yinwu.YinwuRaidPlugin;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灾厄袭击 BossBar 管理器
 * 负责 BossBar 的创建、更新和移除
 */
public class RaidBossBarManager {

    private final YinwuRaidPlugin plugin;
    private final RaidMobManager mobManager;

    // 活跃的 BossBar 映射（玩家 UUID -> BossBar）
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    public RaidBossBarManager(YinwuRaidPlugin plugin, RaidMobManager mobManager) {
        this.plugin = plugin;
        this.mobManager = mobManager;
    }

    /**
     * 清理所有 BossBar
     */
    public void cleanup() {
        int cleanedBossBars = 0;
        for (BossBar bossBar : bossBars.values()) {
            try {
                bossBar.removeAll();
                bossBar.setVisible(false);
                cleanedBossBars++;
            } catch (Exception e) {
                plugin.getLogger().warning("§e\u26A0 清理 BossBar 失败：" + e.getMessage());
            }
        }
        int count = bossBars.size();
        bossBars.clear();
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidBossBarManager] cleanup: 已清理 " + count + " 个BossBar（成功移除 " + cleanedBossBars + " 个）");
        }
    }

    public Map<UUID, BossBar> getBossBars() {
        return bossBars;
    }

    /**
     * 更新 BossBar 显示
     */
    public void updateBossBar(BossBar bossBar, int totalMobs, int remaining) {
        if (bossBar == null) return;

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info(String.format("§e[DEBUG] [RaidBossBarManager] updateBossBar - totalMobs=%d, remaining=%d, aliveCount=%d",
                totalMobs, remaining, getAliveMobCount()));
        }

        double progress = (double) (totalMobs - remaining) / totalMobs;
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

        String title = String.format("§4§l灾厄袭击 §r§7- §c剩余：%d/%d §r§7| §e波次：%d",
            remaining, totalMobs, totalMobs - remaining + 1);
        bossBar.setTitle(title);
    }

    /**
     * 移除 BossBar
     */
    public void removeBossBar(BossBar bossBar) {
        if (bossBar != null) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info(String.format("§e[DEBUG] [RaidBossBarManager] removeBossBar - title=%s, reason=cleanup", bossBar.getTitle()));
            }
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBars.values().remove(bossBar);
        }
    }

    /**
     * 更新 BossBar 显示（波次模式）
     */
    public void updateBossBarForWave(BossBar bossBar, RaidState raidState, int doomLevel) {
        double progress = (double) raidState.currentWave / raidState.totalWaves;
        bossBar.setProgress(progress);

        // 剩余分母 = 本波实际生成数（含每波 1 只幻术师）；未生成前回退到 mobsPerWave
        int currentWaveMobs = raidState.waveMobCount > 0 ? raidState.waveMobCount : raidState.mobsPerWave;
        // 使用本袭击的存活计数（避免并发袭击时显示全局总数），负数封底为 0
        int aliveCount = Math.max(0, raidState.aliveMobs.get());

        bossBar.setTitle(String.format("§4§l灾厄袭击 §r§7- %s §r§c波次：%d/%d §r§7| §e剩余：%d/%d",
            getDifficultyName(doomLevel),
            raidState.currentWave,
            raidState.totalWaves,
            aliveCount,
            currentWaveMobs));
    }

    /**
     * 检查是否所有怪物都已死亡
     */
    public boolean areAllMobsDead() {
        return mobManager.getActiveRaidMobs().isEmpty();
    }

    /**
     * 获取当前存活怪物数（基于集合大小，O(1)）
     */
    public int getAliveMobCount() {
        return mobManager.getActiveRaidMobs().size();
    }

    /**
     * 获取难度名称
     */
    public String getDifficultyName(int doomLevel) {
        return switch (doomLevel) {
            case 7 -> "§a简单";
            case 8 -> "§e普通";
            case 9 -> "§c困难";
            case 10 -> "§4极难";
            default -> "未知";
        };
    }

    /**
     * 将数字转换为罗马数字
     */
    public String getRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(number);
        };
    }
}
