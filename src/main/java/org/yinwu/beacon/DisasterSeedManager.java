package org.yinwu.beacon;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.yinwu.YinwuRaidPlugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DisasterSeedManager {

    private final YinwuRaidPlugin plugin;
    private final NamespacedKey enhanceCountKey; // 累计星星数
    private final NamespacedKey seedUsageKey;    // 消耗种子次数
    // 存储每个附魔的强化上限 (Enchantment Key -> Max Level)
    private final Map<String, Integer> enchantmentLimits = new HashMap<>();

    public DisasterSeedManager(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        this.enhanceCountKey = new NamespacedKey(plugin, "enhance_star_count");
        this.seedUsageKey = new NamespacedKey(plugin, "seed_usage_count");
        loadEnchantmentLimits();
    }

    /**
     * ✅ 从 rewards/config.yml 加载附魔上限配置
     */
    private void loadEnchantmentLimits() {
        File rewardsConfigFile = new File(plugin.getDataFolder(), "rewards/config.yml");
        
        if (!rewardsConfigFile.exists()) {
            plugin.getLogger().warning("§e⚠ 未找到 rewards/config.yml，使用默认附魔上限");
            useDefaultEnchantmentLimits();
            return;
        }
        
        try {
            org.bukkit.configuration.file.YamlConfiguration rewardsConfig = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(rewardsConfigFile);
            
            org.bukkit.configuration.ConfigurationSection limitsSection = 
                rewardsConfig.getConfigurationSection("enhancement.limits");
            
            if (limitsSection != null) {
                for (String enchantName : limitsSection.getKeys(false)) {
                    int maxLevel = limitsSection.getInt(enchantName);
                    enchantmentLimits.put(enchantName.toUpperCase(), maxLevel);
                }
                plugin.getLogger().fine("§a已加载 " + enchantmentLimits.size() + " 个附魔的强化上限配置。");
            } else {
                plugin.getLogger().warning("§e⚠ rewards/config.yml 中未找到 enhancement.limits 配置，使用默认值");
                useDefaultEnchantmentLimits();
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 加载附魔上限配置失败", e);
            useDefaultEnchantmentLimits();
        }
    }
    
    /**
     * 使用默认附魔上限
     */
    private void useDefaultEnchantmentLimits() {
        enchantmentLimits.put("EFFICIENCY", 10);
        enchantmentLimits.put("SHARPNESS", 10);
        enchantmentLimits.put("PROTECTION", 8);
        enchantmentLimits.put("UNBREAKING", 5);
        enchantmentLimits.put("FORTUNE", 5);
        enchantmentLimits.put("LOOTING", 5);
        plugin.getLogger().fine("§a已使用默认附魔上限配置");
    }

    /**
     * 检查物品是否可以继续消耗种子进行强化
     */
    public boolean canEnhance(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        // ✅ 使用 ConfigManager 获取最大强化次数
        org.yinwu.config.ConfigManager configManager = plugin.getConfigManager();
        org.yinwu.config.ConfigManager.EnhancementConfig enhancementConfig = configManager.getEnhancementConfig();
        int maxUsage = enhancementConfig != null ? enhancementConfig.getMaxEnhanceCount() : 3;
        int usage = meta.getPersistentDataContainer().getOrDefault(seedUsageKey, PersistentDataType.INTEGER, 0);
        boolean result = usage < maxUsage;
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] canEnhance: 物品类型=" + (item != null ? item.getType().name() : "null") + ", 当前使用次数=" + usage + ", 最大次数=" + maxUsage + ", 结果=" + result);
        }
        return result;
    }

    public int getStarCount(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        return meta.getPersistentDataContainer().getOrDefault(enhanceCountKey, PersistentDataType.INTEGER, 0);
    }

    private void setStarCount(ItemStack item, int count) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(enhanceCountKey, PersistentDataType.INTEGER, count);
            item.setItemMeta(meta);
        }
    }

    private void incrementSeedUsage(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            int currentUsage = meta.getPersistentDataContainer().getOrDefault(seedUsageKey, PersistentDataType.INTEGER, 0);
            meta.getPersistentDataContainer().set(seedUsageKey, PersistentDataType.INTEGER, currentUsage + 1);
            item.setItemMeta(meta);
        }
    }

    /**
     * 执行强化逻辑
     * @param targetItem 目标物品 (29槽)
     * @param seedItem 灾厄之种 (31槽)
     * @return 强化后的物品，如果失败返回 null
     */
    public ItemStack performEnhancement(ItemStack targetItem, ItemStack seedItem) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] performEnhancement: 目标物品=" + (targetItem != null ? targetItem.getType().name() : "null") + ", 种子物品=" + (seedItem != null ? seedItem.getType().name() : "null"));
        }

        if (!canEnhance(targetItem)) return null;

        int times = calculateEnhancementTimes(seedItem);
        if (times <= 0) return null;

        // 获取可强化的附魔列表（仅用于筛选黑名单和初始检查）
        Map<Enchantment, Integer> currentEnchants = targetItem.getEnchantments();
        List<Enchantment> baseEnchants = new ArrayList<>();
        
        for (Map.Entry<Enchantment, Integer> entry : currentEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            if (isEnhanceable(ench)) {
                baseEnchants.add(ench);
            }
        }

        if (baseEnchants.isEmpty()) return null;

        // 随机选择附魔进行强化
        ItemMeta meta = targetItem.getItemMeta();
        if (meta == null) return null;

        int totalEnhancePoints = 0; // 记录本次强化实际获得的等级提升总数

        for (int i = 0; i < times; i++) {
            // ✅ 关键修复：每次循环都实时从 meta 获取当前等级，而不是用旧的 currentEnchants
            List<Enchantment> availableEnchants = new ArrayList<>();
            for (Enchantment ench : baseEnchants) {
                int currentLevel = meta.getEnchantLevel(ench);
                int maxLevel = getMaxLevel(ench);
                if (currentLevel < maxLevel) {
                    availableEnchants.add(ench);
                }
            }

            if (availableEnchants.isEmpty()) break;

            Enchantment selected = availableEnchants.get(ThreadLocalRandom.current().nextInt(availableEnchants.size()));
            int currentLevel = meta.getEnchantLevel(selected);
            int newLevel = currentLevel + 1;
            meta.addEnchant(selected, newLevel, true);
            totalEnhancePoints++; // 每成功强化一次，累计点数 +1
        }

        // 如果没有任何一次强化成功，则不消耗种子
        if (totalEnhancePoints == 0) {
            return null;
        }

        targetItem.setItemMeta(meta);
        
        // 更新 PDC：
        // 1. 累计星星数 = 原有星星 + 本次成功强化的次数
        int currentStars = getStarCount(targetItem) + totalEnhancePoints;
        setStarCount(targetItem, currentStars);
        
        // 2. 消耗种子次数 +1
        incrementSeedUsage(targetItem);
        
        renameItem(targetItem, currentStars);

        return targetItem;
    }

    private int calculateEnhancementTimes(ItemStack seed) {
        if (seed == null) return 0;
        
        // 获取显示名称并去除颜色代码
        String name = "";
        if (seed.hasItemMeta() && seed.getItemMeta().hasDisplayName()) {
            name = ChatColor.stripColor(seed.getItemMeta().getDisplayName());
        }
        
        double roll = ThreadLocalRandom.current().nextDouble();
        
        // ✅ 从 ConfigManager 获取缓存的种子配置，避免每次直接读取文件
        org.yinwu.config.ConfigManager.EnhancementConfig enhancementConfig =
            plugin.getConfigManager().getEnhancementConfig();
        
        if (enhancementConfig == null) {
            plugin.getLogger().warning("§c✗ 强化配置未加载");
            return 0;
        }
        
        Map<String, Map<Integer, Double>> seedChances = enhancementConfig.getSeedChances();
        Map<String, String> seedNames = enhancementConfig.getSeedNames();
        
        if (seedChances == null || seedChances.isEmpty() || seedNames == null || seedNames.isEmpty()) {
            plugin.getLogger().warning("§c✗ 灾厄之种概率配置为空");
            return 0;
        }
        
        // 按名称匹配种子配置，收集所有匹配项后选择名称最长的（最精确的匹配）
        String bestMatchKey = null;
        int bestMatchLength = 0;
        
        for (Map.Entry<String, String> entry : seedNames.entrySet()) {
            String seedKey = entry.getKey();
            String seedName = entry.getValue();
            String cleanSeedName = ChatColor.stripColor(seedName);
            
            if (name.contains(cleanSeedName)) {
                if (bestMatchKey == null || cleanSeedName.length() > bestMatchLength) {
                    bestMatchKey = seedKey;
                    bestMatchLength = cleanSeedName.length();
                }
            }
        }
        
        if (bestMatchKey == null) {
            plugin.getLogger().warning("§c[DEBUG] 未找到匹配的灾厄之种配置！物品名称: " + name);
            return 0;
        }
        
        // 获取对应种子的概率配置
        Map<Integer, Double> chances = seedChances.get(bestMatchKey);
        if (chances == null || chances.isEmpty()) {
            plugin.getLogger().warning("§c[DEBUG] 种子 '" + bestMatchKey + "' 的概率配置为空");
            return 0;
        }
        
        // 按次数升序遍历概率
        List<Integer> sortedTimes = new ArrayList<>(chances.keySet());
        Collections.sort(sortedTimes);
        
        double cumulativeProbability = 0.0;
        int maxTimes = sortedTimes.get(sortedTimes.size() - 1);
        
        for (int times : sortedTimes) {
            double probability = chances.get(times);
            cumulativeProbability += probability;
            
            if (roll < cumulativeProbability) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] calculateEnhancementTimes: 匹配种子=" + bestMatchKey + ", roll=" + String.format("%.4f", roll) + ", 累积概率=" + String.format("%.4f", cumulativeProbability) + ", 返回次数=" + times);
                }
                return times;
            }
        }
        
        // 如果没有匹配到任何概率（roll 超过了总和），返回最大次数
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] calculateEnhancementTimes: 匹配种子=" + bestMatchKey + ", roll=" + String.format("%.4f", roll) + ", 返回次数=" + maxTimes);
        }
        return maxTimes;
    }

    private void renameItem(ItemStack item, int count) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] renameItem: 物品类型=" + (item != null ? item.getType().name() : "null") + ", 星星数=" + count);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 1. 处理名称：去除可能存在的旧星星，保持名称纯净
            String currentName = meta.hasDisplayName() ? meta.getDisplayName() : item.getType().toString();
            String originalName = currentName.replaceAll("^(★)+", "");
            meta.setDisplayName(originalName);

            // 2. 生成星星字符串
            StringBuilder stars = new StringBuilder();
            for (int i = 0; i < count; i++) stars.append(ChatColor.RED + "★");
            String starLore = stars.toString();

            // 3. 处理 Lore
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            
            // 查找是否已经存在星星 Lore（避免重复添加）
            boolean found = false;
            for (int i = 0; i < lore.size(); i++) {
                // 简单判断：如果包含星星，则认为是强化星级行
                if (lore.get(i).contains("★")) {
                    lore.set(i, starLore); // 更新现有星星
                    found = true;
                    break;
                }
            }
            if (!found) {
                lore.add(starLore); // 如果没有，则追加到 Lore 末尾
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private int getMaxLevel(Enchantment ench) {
        String key = ench.getKey().getKey();
        
        // 【完全独立】仅从 ConfigManager 的 enhancement.limits 中读取灾厄强化上限
        org.yinwu.config.ConfigManager configManager = plugin.getConfigManager();
        org.yinwu.config.ConfigManager.EnhancementConfig enhancementConfig = configManager.getEnhancementConfig();
        
        if (enhancementConfig != null && enhancementConfig.getLimits() != null) {
            Integer limit = enhancementConfig.getLimits().get(key);
            if (limit != null) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] getMaxLevel: 附魔=" + key + ", 上限=" + limit);
                }
                return limit;
            }
        }
        
        // 如果 config.yml 中没有配置该附魔的上限，则使用一个较高的默认值（如 10）
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] getMaxLevel: 附魔=" + key + ", 未配置上限，使用默认值10");
        }
        return 10;
    }

    private boolean isEnhanceable(Enchantment ench) {
        // 排除经验修补等不可强化附魔
        if (ench == Enchantment.MENDING) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] isEnhanceable: 附魔=" + ench.getKey().getKey() + ", 结果=不可强化（经验修补）");
            }
            return false;
        }
        
        // ✅ 从 ConfigManager 读取 blacklist
        org.yinwu.config.ConfigManager configManager = plugin.getConfigManager();
        org.yinwu.config.ConfigManager.EnhancementConfig enhancementConfig = configManager.getEnhancementConfig();
        java.util.List<String> blacklist = enhancementConfig != null ? enhancementConfig.getBlacklist() : new java.util.ArrayList<>();
        boolean result = !blacklist.contains(ench.getKey().getKey());
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [DisasterSeedManager] isEnhanceable: 附魔=" + ench.getKey().getKey() + ", 结果=" + result);
        }
        return result;
    }
}