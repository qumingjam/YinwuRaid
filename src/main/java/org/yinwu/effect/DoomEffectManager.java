package org.yinwu.effect;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.yinwu.YinwuRaidPlugin;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灾厄效果管理器
 * 管理玩家的灾厄 buff
 */
public class DoomEffectManager {
    
    private final YinwuRaidPlugin plugin;
    
    // 自定义灾厄效果类型（使用现有效果模拟）
    // 1.21.11 中 BAD_OMEN 进入村庄后会转换为 RAID_OMEN
    private final PotionEffectType doomEffectType;
    private final PotionEffectType raidOmenType;
    
    public DoomEffectManager(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        this.doomEffectType = PotionEffectType.BAD_OMEN;
        this.raidOmenType = PotionEffectType.RAID_OMEN;
    }
    
    /**
     * 对玩家施加灾厄效果
     * 
     * @param player 目标玩家
     * @param beaconLevel 信标等级 (1-4 或 6=彩蛋级)
     * @param duration 持续时间（tick）
     */
    public void applyDoomEffect(Player player, int beaconLevel, int duration) {
        if (doomEffectType == null) {
            plugin.getLogger().warning("无法获取灾厄效果类型！");
            return;
        }
        
        // ✅ 从 ConfigManager 读取灾厄等级映射，支持自定义
        org.yinwu.config.ConfigManager configManager = plugin.getConfigManager();
        org.yinwu.config.ConfigManager.BeaconConfig beaconConfig = configManager.getBeaconConfig();
        int doomLevel = beaconConfig.getDoomLevels().getOrDefault(beaconLevel, beaconLevel + 6);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DoomEffectManager] 施加灾厄效果: 玩家=" + player.getName() + ", 信标等级=" + beaconLevel + ", 灾厄等级=" + doomLevel + ", 持续时间=" + duration + " tick");
        }
        
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) {
                // 玩家不在线，取消操作
                return;
            }
            
            // 移除已有的效果
            player.removePotionEffect(doomEffectType);
            player.removePotionEffect(raidOmenType);
            
            // 添加新的高等级灾厄效果
            PotionEffect effect = new PotionEffect(
                doomEffectType,
                duration,
                // Bukkit 的等级从 0 开始计算
                doomLevel - 1,
                // 不显示粒子效果
                false,
                // 显示图标
                true,
                // 永久持续（我们已经设置了持续时间）
                true
            );
            
            player.addPotionEffect(effect);
            
            // ✅ 彩蛋级特殊日志
            if (org.yinwu.enums.BeaconLevel.fromInt(beaconLevel).isEasterEgg()) {
                plugin.getLogger().info(String.format("§d✨ 已对玩家 %s 施加彩蛋级灾厄效果（等级 %d）", player.getName(), doomLevel));
            } else if (ThreadLocalRandom.current().nextDouble() < 0.1) {
                plugin.getLogger().fine(String.format("§a✓ 已对玩家 %s 施加灾厄效果等级 %d", player.getName(), doomLevel));
            }
        });
    }
    
    /**
     * 检查玩家是否携带灾厄效果
     * 
     * @param player 玩家
     * @return 是否携带灾厄效果
     */
    public boolean hasDoomEffect(Player player) {
        return player.hasPotionEffect(doomEffectType);
    }
    
    /**
     * 获取玩家的灾厄效果等级
     * 
     * @param player 玩家
     * @return 效果等级，0 表示没有效果
     */
    public int getDoomLevel(Player player) {
        // 优先检查 RAID_OMEN（进入村庄后）
        PotionEffect effect = player.getPotionEffect(raidOmenType);
        if (effect != null) {
            int level = effect.getAmplifier() + 1;
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [DoomEffectManager] getDoomLevel: 玩家=" + player.getName() + ", 等级=" + level + ", 来源=RAID_OMEN");
            }
            return level;
        }
        
        // 其次检查 BAD_OMEN（进入村庄前）
        effect = player.getPotionEffect(doomEffectType);
        if (effect != null) {
            int level = effect.getAmplifier() + 1;
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [DoomEffectManager] getDoomLevel: 玩家=" + player.getName() + ", 等级=" + level + ", 来源=BAD_OMEN");
            }
            return level;
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DoomEffectManager] getDoomLevel: 玩家=" + player.getName() + ", 等级=0（无效果）");
        }
        return 0;
    }
    
    /**
     * 移除玩家的灾厄效果
     * 
     * @param player 玩家
     */
    public void removeDoomEffect(Player player) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DoomEffectManager] 移除灾厄效果: 玩家=" + player.getName());
        }
        
        if (doomEffectType != null) {
            player.removePotionEffect(doomEffectType);
        }
    }
    
    /**
     * ✅ 清理所有资源（插件禁用时调用）
     * 直接操作玩家，不调度新任务（插件已禁用时无法注册调度）
     */
    public void cleanup() {
        plugin.getLogger().info("§6[YinwuRaid] §e正在清理灾厄效果...");
        
        int clearedPlayers = 0;
        
        // 直接清除所有在线玩家的灾厄效果（插件禁用时无法注册新调度）
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                if (player.isOnline() && hasDoomEffect(player)) {
                    removeDoomEffect(player);
                    clearedPlayers++;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("§e⚠ 清理玩家 " + player.getName() + " 的灾厄效果失败：" + e.getMessage());
            }
        }
        
        plugin.getLogger().info(String.format("§a✓ 已清除 %d 个玩家的灾厄效果", clearedPlayers));
    }
}