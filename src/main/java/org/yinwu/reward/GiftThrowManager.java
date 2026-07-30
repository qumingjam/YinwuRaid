package org.yinwu.reward;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 村民赠礼投掷管理器
 * 定时检测附近带有村庄英雄效果的玩家并给予奖励
 */
public class GiftThrowManager {
    private final YinwuRaidPlugin plugin;
    private final VillagerRewardManager rewardManager;

    // 冷却时间存储：key = "villagerUuid-playerUuid", value = 冷却结束时间戳
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    /** 有村庄英雄效果的玩家缓存，定时任务只遍历这里 */
    private final Set<UUID> affectedPlayers = ConcurrentHashMap.newKeySet();

    private io.papermc.paper.threadedregions.scheduler.ScheduledTask detectionTask;

    // 检测范围（格）
    private final double detectionRange;
    // 最小冷却时间（秒）
    private final int minCooldown;
    // 最大冷却时间（秒）
    private final int maxCooldown;
    // ✅ 赠礼检测间隔（tick）
    private final long detectionInterval;

    public GiftThrowManager(YinwuRaidPlugin plugin, VillagerRewardManager rewardManager) {
        this.plugin = plugin;
        this.rewardManager = rewardManager;

        // ✅ 使用 ConfigManager 获取村民赠礼配置
        ConfigManager configManager = plugin.getConfigManager();
        org.yinwu.config.VillagerGiftConfig giftConfig = configManager.getEntityConfig().getVillagerGift();
        this.detectionRange = giftConfig.getRange();
        this.minCooldown = giftConfig.getMinCooldown();
        this.maxCooldown = giftConfig.getMaxCooldown();
        this.detectionInterval = giftConfig.getDetectionInterval();

        startDetectionTask();
    }

    private void startDetectionTask() {
        this.detectionTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            try {
                // 使用缓存玩家遍历（ConcurrentHashMap.newKeySet 线程安全）
                for (Player player : getCachedPlayers()) {
                    if (!player.isOnline()) continue;
                    Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (regionTask) -> {
                        int level = getHeroLevel(player);
                        if (level >= 6) affectedPlayers.add(player.getUniqueId());
                        if (level >= 6) checkAndGiveGifts(player);
                    });
                }
                cleanupExpiredCooldowns();
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 村民赠礼检测异常", e);
            }
        }, 1L, detectionInterval); // ✅ 初始延迟至少 1 tick（Folia 要求），检测间隔从配置读取

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [GiftThrowManager] 检测任务已启动, 检测间隔=" + detectionInterval + " tick");
        }
    }

    private void checkAndGiveGifts(Player player) {
        if (!player.isOnline()) return;

        int heroLevel = getHeroLevel(player);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [GiftThrowManager] 检测玩家: " + player.getName() + ", 英雄等级=" + heroLevel + ", 附近村民数=" + getNearbyVillagersOptimized(player, detectionRange).size());
        }

        if (heroLevel < 6 || heroLevel > 10) return;

        Collection<Villager> villagers = getNearbyVillagersOptimized(player, detectionRange);

        int maxCheckCount = 10;
        int checkedCount = 0;
        long delayTicks = 1; // ✅ 延迟计数器（Folia 要求 >= 1）

        for (Villager villager : villagers) {
            if (checkedCount >= maxCheckCount) break;

            String cooldownKey = getCooldownKey(villager, player);

            if (isOnCooldown(cooldownKey)) {
                continue;
            }

            // ✅ 分批次延迟投掷：每个村民间隔 5 tick（0.25 秒）
            final long currentDelay = delayTicks;
            setCooldown(cooldownKey);
            villager.getScheduler().runDelayed(plugin, t -> {
                if (!player.isOnline() || !villager.isValid() || villager.isDead()) return;
                giveGift(villager, player, heroLevel);
            }, () -> {}, currentDelay);

            checkedCount++;
            delayTicks += 5; // ✅ 下一个村民延迟 5 tick
        }
    }

    private int getHeroLevel(Player player) {
        var effect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.RAID_OMEN);
        if (effect != null) {
            int level = effect.getAmplifier() + 1;
            return (level >= 6 && level <= 10) ? level : 0;
        }

        effect = player.getPotionEffect(org.bukkit.potion.PotionEffectType.HERO_OF_THE_VILLAGE);
        if (effect != null) {
            int level = effect.getAmplifier() + 1;
            return (level >= 6 && level <= 10) ? level : 0;
        }

        return 0;
    }

    private Collection<Villager> getNearbyVillagersOptimized(Player player, double range) {
        try {
            Collection<org.bukkit.entity.Entity> nearbyEntities = player.getLocation().getNearbyEntities(range, range, range);
            List<Villager> villagers = new ArrayList<>();
            for (org.bukkit.entity.Entity entity : nearbyEntities) {
                if (entity instanceof Villager && entity.isValid() && !entity.isDead()) {
                    villagers.add((Villager) entity);
                }
            }
            return villagers;
        } catch (Exception e) {
            plugin.getLogger().warning("§e⚠ 获取附近村民失败：" + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void giveGift(Villager villager, Player player, int heroLevel) {
        if (!villager.isValid() || villager.isDead()) {
            return;
        }

        ItemStack reward = rewardManager.selectReward(villager, heroLevel);

        if (reward == null) {
            return;
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            String villagerType = villager.getProfession() != null ? villager.getProfession().getKey().getKey() : "none";
            String rewardName = reward.hasItemMeta() && reward.getItemMeta().hasDisplayName() ? reward.getItemMeta().getDisplayName() : reward.getType().name();
            plugin.getLogger().info("§e[DEBUG] [GiftThrowManager] 赠礼: 村民类型=" + villagerType + ", 玩家=" + player.getName() + ", 英雄等级=" + heroLevel + ", 物品=" + rewardName);
        }

        Location dropLoc = villager.getLocation().add(0, 1.5, 0);
        villager.getWorld().dropItemNaturally(dropLoc, reward);

        // Raid ↔ Enchant 联动：15% 概率额外掉落自定义附魔书
        if (rewardManager.hasEnchantIntegration() && ThreadLocalRandom.current().nextDouble() < 0.15) {
            ItemStack bonus = rewardManager.trySelectBonusEnchantBook();
            if (bonus != null) {
                villager.getWorld().dropItemNaturally(dropLoc, bonus);
            }
        }

        try {
            villager.getWorld().playSound(dropLoc, Sound.ENTITY_ITEM_PICKUP, 0.3f, 1.2f);
        } catch (Exception e) {
            plugin.getLogger().warning("播放音效失败: " + e.getMessage());
        }

        villager.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dropLoc, 2, 0.2, 0.2, 0.2, 0.01);

        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (t) -> {
            if (!player.isOnline()) return;

            player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§a✓ 村民赠送了礼物：" + reward.getItemMeta().getDisplayName()));
        });
    }

    /**
     * 生成冷却键
     */
    private String getCooldownKey(Villager villager, Player player) {
        return villager.getUniqueId().toString() + "-" + player.getUniqueId().toString();
    }

    /**
     * 检查是否在冷却中
     */
    /** 从缓存获取有村庄英雄效果的玩家列表 */
    private List<Player> getCachedPlayers() {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : affectedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) list.add(p);
            else affectedPlayers.remove(uuid);
        }
        return list;
    }

    private boolean isOnCooldown(String key) {
        Long cooldownTime = cooldowns.get(key);

        if (cooldownTime == null) return false;

        long now = System.currentTimeMillis();
        return now < cooldownTime;
    }

    /**
     * 设置冷却时间
     */
    private void setCooldown(String key) {
        // ✅ 从配置读取冷却时间（使用最小值）
        int cooldownSeconds = minCooldown;
        long cooldownMillis = cooldownSeconds * 1000L;

        cooldowns.put(key, System.currentTimeMillis() + cooldownMillis);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [GiftThrowManager] 设置冷却: key=" + key + ", seconds=" + cooldownSeconds);
        }
    }

    /**
     * 清理过期冷却数据（可选，防止内存泄漏）
     */
    public void cleanupExpiredCooldowns() {
        long now = System.currentTimeMillis();
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < now);
    }

    public void cleanup() {
        if (detectionTask != null) {
            detectionTask.cancel();
            detectionTask = null;
        }
        affectedPlayers.clear();
        cooldowns.clear();
    }
}
