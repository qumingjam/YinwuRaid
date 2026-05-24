package org.yinwu.reward;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 单个奖励条目配置
 */
public class RewardEntry {
    public Material material;
    public int minAmount;
    public int maxAmount;
    public double chance;
    public String displayName;
    public List<EnchantmentData> possibleEnchantments;
    public String professionType; // ✅ 新增：职业类型（librarian, armorer, toolsmith, weaponsmith）
    public String enchantmentType; // ✅ 新增：附魔逻辑类型（librarian, armorer, smith, default）
    
    /**
     * 从配置创建奖励物品
     * 
     * @param doomLevel 灾厄等级（7-10），用于决定附魔数量
     */
    public ItemStack createItem(int doomLevel) {
        // 随机数量
        int amount = minAmount + ThreadLocalRandom.current().nextInt(maxAmount - minAmount + 1);
        ItemStack item = new ItemStack(material, amount);
        
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 设置自定义名称
            if (displayName != null) {
                meta.setDisplayName(displayName);
            }
            
            // ✅ 添加附魔（如果有）
            if (possibleEnchantments != null && !possibleEnchantments.isEmpty()) {
                // ✅ 根据职业类型和英雄等级决定附魔逻辑
                applyEnchantments(meta, doomLevel);
            }
            
            // ✅ 设置耐久度为最大值（显示满耐久条）
            if (meta instanceof org.bukkit.inventory.meta.Damageable) {
                org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) meta;
                damageable.setDamage(0); // 0 = 满耐久
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * 无参数的兼容方法，默认使用灾厄等级 7
     */
    public ItemStack createItem() {
        return createItem(7);
    }
    
    /**
     * ✅ 根据职业类型和英雄等级应用附魔
     * 
     * @param meta 物品元数据
     * @param heroLevel 村庄英雄等级（7-10）
     */
    private void applyEnchantments(ItemMeta meta, int heroLevel) {
        if (possibleEnchantments == null || possibleEnchantments.isEmpty()) {
            return;
        }
        
        // ✅ 优先使用配置的附魔类型，否则根据职业类型决定
        if (enchantmentType != null && !enchantmentType.isEmpty()) {
            // 使用配置的附魔类型
            switch (enchantmentType.toLowerCase()) {
                case "librarian":
                    applyLibrarianEnchantments(meta, heroLevel);
                    break;
                case "armorer":
                    applyArmorerEnchantments(meta, heroLevel);
                    break;
                case "smith":
                    applySmithEnchantments(meta, heroLevel);
                    break;
                default:
                    applyLibrarianEnchantments(meta, heroLevel);
            }
        } else if (professionType != null && !"default".equals(professionType)) {
            // 使用职业类型
            if ("librarian".equals(professionType)) {
                applyLibrarianEnchantments(meta, heroLevel);
            } else if ("armorer".equals(professionType)) {
                applyArmorerEnchantments(meta, heroLevel);
            } else if ("toolsmith".equals(professionType) || "weaponsmith".equals(professionType)) {
                applySmithEnchantments(meta, heroLevel);
            } else {
                applyLibrarianEnchantments(meta, heroLevel);
            }
        } else {
            // 默认按图书管理员处理
            applyLibrarianEnchantments(meta, heroLevel);
        }
    }
    
    /**
     * ✅ 图书管理员附魔逻辑（普通附魔，不突破上限）
     * 7 级：2 个附魔
     * 8 级：3 个附魔
     * 9 级：4 个附魔
     * 10 级：5 个附魔
     * 
     * ⚠️ 注意：如果配置中包含冲突附魔（如四种保护），
     * 生成的附魔书在铁砧上可能无法使用（原版限制）
     */
    /**
     * 图书管理员附魔（随机选择基础附魔，不强化，去重）
     */
    private void applyLibrarianEnchantments(ItemMeta meta, int heroLevel) {
        int baseCount = getBaseEnchantCount(heroLevel, "librarian");
        
        if (possibleEnchantments == null || possibleEnchantments.isEmpty()) {
            return;
        }
        
        // ✅ 记录已选择的附魔类型（去重）
        java.util.Set<Enchantment> selectedTypes = new java.util.HashSet<>();
        
        // ✅ 随机选择基础附魔（去重）
        for (int i = 0; i < baseCount; i++) {
            EnchantmentData enchantData;
            int attempts = 0;
            
            // 尝试选择不重复的附魔
            do {
                enchantData = possibleEnchantments.get(ThreadLocalRandom.current().nextInt(possibleEnchantments.size()));
                attempts++;
                
                // 如果已选择过该附魔类型，重新选择
                if (selectedTypes.contains(enchantData.enchantment)) {
                    continue;
                }
                
                // 找到未选择的附魔，退出循环
                break;
            } while (attempts < 50);  // 最多尝试 50 次
            
            // 记录已选择的附魔类型
            selectedTypes.add(enchantData.enchantment);
            
            if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                ((org.bukkit.inventory.meta.EnchantmentStorageMeta) meta)
                    .addStoredEnchant(enchantData.enchantment, enchantData.level, true);
            }
        }
        
        // ✅ 不再进行强化（getBoostCount 返回 0）
    }
    
    /**
     * 盔甲匠附魔（随机选择基础附魔，不强化，去重）
     */
    private void applyArmorerEnchantments(ItemMeta meta, int heroLevel) {
        int baseCount = getBaseEnchantCount(heroLevel, "armorer");
        
        if (possibleEnchantments == null || possibleEnchantments.isEmpty()) {
            return;
        }
        
        // ✅ 记录已选择的附魔类型（去重）
        java.util.Set<Enchantment> selectedTypes = new java.util.HashSet<>();
        
        // ✅ 随机选择基础附魔（去重）
        for (int i = 0; i < baseCount; i++) {
            EnchantmentData enchantData;
            int attempts = 0;
            
            // 尝试选择不重复的附魔
            do {
                enchantData = possibleEnchantments.get(ThreadLocalRandom.current().nextInt(possibleEnchantments.size()));
                attempts++;
                
                // 如果已选择过该附魔类型，重新选择
                if (selectedTypes.contains(enchantData.enchantment)) {
                    continue;
                }
                
                // 找到未选择的附魔，退出循环
                break;
            } while (attempts < 50);  // 最多尝试 50 次
            
            // 记录已选择的附魔类型
            selectedTypes.add(enchantData.enchantment);
            
            meta.addEnchant(enchantData.enchantment, enchantData.level, true);
        }
        
        // ✅ 不再进行强化（getBoostCount 返回 0）
    }
    
    /**
     * 工具商/武器商附魔（随机选择基础附魔，不强化，去重）
     */
    private void applySmithEnchantments(ItemMeta meta, int heroLevel) {
        int baseCount = getBaseEnchantCount(heroLevel, "smith");
        
        if (possibleEnchantments == null || possibleEnchantments.isEmpty()) {
            return;
        }
        
        // ✅ 记录已选择的附魔类型（去重）
        java.util.Set<Enchantment> selectedTypes = new java.util.HashSet<>();
        
        // ✅ 随机选择基础附魔（去重）
        for (int i = 0; i < baseCount; i++) {
            EnchantmentData enchantData;
            int attempts = 0;
            
            // 尝试选择不重复的附魔
            do {
                enchantData = possibleEnchantments.get(ThreadLocalRandom.current().nextInt(possibleEnchantments.size()));
                attempts++;
                
                // 如果已选择过该附魔类型，重新选择
                if (selectedTypes.contains(enchantData.enchantment)) {
                    continue;
                }
                
                // 找到未选择的附魔，退出循环
                break;
            } while (attempts < 50);  // 最多尝试 50 次
            
            // 记录已选择的附魔类型
            selectedTypes.add(enchantData.enchantment);
            
            meta.addEnchant(enchantData.enchantment, enchantData.level, true);
        }
        
        // ✅ 不再进行强化（getBoostCount 返回 0）
    }
    
    /**
     * ✅ 计算基础附魔数量（从 ConfigManager 读取配置）
     */
    private int getBaseEnchantCount(int heroLevel, String professionType) {
        YinwuRaidPlugin plugin = YinwuRaidPlugin.getInstance();
        if (plugin == null) return 1;
        
        ConfigManager configManager = plugin.getConfigManager();
        if (configManager == null) return 1;
        
        ConfigManager.EnhancementConfig enhancementConfig = configManager.getEnhancementConfig();
        if (enhancementConfig == null) return 1;
        
        Map<String, Map<Integer, Integer>> enchantCounts = enhancementConfig.getEnchantCounts();
        if (enchantCounts == null) return 1;
        
        Map<Integer, Integer> levelMap = enchantCounts.get(professionType);
        if (levelMap == null) return 1;
        
        return levelMap.getOrDefault(heroLevel, 1);
    }
    
    /**
     * ✅ 计算强化次数（村民赠礼不强化，始终返回 0）
     */
    private int getBoostCount(int heroLevel) {
        return 0;  // ✅ 村民赠礼不进行任何强化
    }
    
    /**
     * ✅ 检查附魔是否在黑名单中（每次直接从 ConfigManager 加载，无需缓存）
     */
    private boolean isBlacklisted(Enchantment enchantment) {
        YinwuRaidPlugin plugin = YinwuRaidPlugin.getInstance();
        if (plugin == null) return false;
        
        List<String> blacklist = plugin.getConfigManager().getEnhancementConfig().getBlacklist();
        if (blacklist == null || blacklist.isEmpty()) {
            return false;
        }
        
        String key = enchantment.getKey().getKey();
        return blacklist.contains(key);
    }
    
    /**
     * 通用附魔强化逻辑：从可用池中随机选择附魔进行强化
     * 
     * @param meta 物品元数据
     * @param allEnchants 所有可能的附魔
     * @param excluded 被排除的附魔（不参与强化）
     * @param boostCount 强化次数
     * @param isBook 是否为附魔书（使用 addStoredEnchant）
     */
    private void applyRandomBoost(ItemMeta meta, List<EnchantmentData> allEnchants, 
                                   List<org.bukkit.enchantments.Enchantment> excluded,
                                   int boostCount, boolean isBook) {
        if (allEnchants == null || allEnchants.isEmpty()) {
            return;
        }
        
        for (int i = 0; i < boostCount; i++) {
            EnchantmentData enchantData = allEnchants.get(ThreadLocalRandom.current().nextInt(allEnchants.size()));
            
            // ✅ 检查是否在物品专属排除列表中
            boolean shouldSkip = false;
            if (excluded != null) {
                for (org.bukkit.enchantments.Enchantment excl : excluded) {
                    if (enchantData.enchantment.equals(excl)) {
                        shouldSkip = true;
                        break;
                    }
                }
            }
            
            // ✅ 检查是否在全局黑名单中（config.yml）
            if (!shouldSkip && isBlacklisted(enchantData.enchantment)) {
                shouldSkip = true;
            }
            
            if (shouldSkip) {
                continue;
            }
            
            int currentLevel = 0;
            if (isBook && meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                currentLevel = ((org.bukkit.inventory.meta.EnchantmentStorageMeta) meta)
                    .getStoredEnchantLevel(enchantData.enchantment);
            } else {
                currentLevel = meta.getEnchantLevel(enchantData.enchantment);
            }
            
            int newLevel = Math.max(enchantData.level, currentLevel) + 1;
            
            if (isBook && meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta) {
                ((org.bukkit.inventory.meta.EnchantmentStorageMeta) meta)
                    .addStoredEnchant(enchantData.enchantment, newLevel, true);
            } else {
                meta.addEnchant(enchantData.enchantment, newLevel, true);
            }
        }
    }
    
    /**
     * 从配置段加载奖励条目
     */
    public static RewardEntry fromConfig(ConfigurationSection section) {
        RewardEntry entry = new RewardEntry();
        
        String materialName = section.getString("material");
        try {
            entry.material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的物品材料：" + materialName);
        }
        
        String amountRange = section.getString("amount", "1");
        int[] range = parseAmountRange(amountRange);
        entry.minAmount = range[0];
        entry.maxAmount = range[1];
        
        entry.chance = section.getDouble("chance", 1.0);
        entry.displayName = section.getString("display-name");
        
        entry.enchantmentType = section.getString("enchantment-type");
        
        // 加载附魔配置
        ConfigurationSection enchantSection = section.getConfigurationSection("enchantments");
        if (enchantSection != null) {
            entry.possibleEnchantments = loadEnchantments(enchantSection);
        }
        
        return entry;
    }
    
    /**
     * ✅ 从 Map 加载奖励条目（更安全的方法）
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    public static RewardEntry fromMap(Map<String, Object> map) {
        RewardEntry entry = new RewardEntry();
        
        String materialName = (String) map.get("material");
        try {
            entry.material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的物品材料：" + materialName);
        }
        
        String amountRange = convertToString(map.getOrDefault("amount", "1"));
        int[] range = parseAmountRange(amountRange);
        entry.minAmount = range[0];
        entry.maxAmount = range[1];
        
        Object chanceObj = map.get("chance");
        entry.chance = (chanceObj instanceof Number) ? ((Number) chanceObj).doubleValue() : 1.0;
        entry.displayName = (String) map.get("display-name");
        
        entry.enchantmentType = convertToString(map.get("enchantment-type"));
        
        // 加载附魔配置
        Object enchantObj = map.get("enchantments");
        if (enchantObj instanceof Map) {
            Map<String, Object> enchantMap = (Map<String, Object>) enchantObj;
            Object possibleEnchantsObj = enchantMap.get("possible-enchantments");
            if (possibleEnchantsObj instanceof List) {
                entry.possibleEnchantments = loadEnchantmentsFromList((List<?>) possibleEnchantsObj);
            }
        }
        
        return entry;
    }
    
    /**
     * 将对象转换为字符串（处理 Integer、Double 等类型）
     */
    private static String convertToString(Object obj) {
        if (obj == null) return "";
        if (obj instanceof String) return (String) obj;
        return obj.toString();
    }
    
    /**
     * 解析数量范围（如 "1-5" 或 "3"）
     */
    private static int[] parseAmountRange(String range) {
        if (range.contains("-")) {
            String[] parts = range.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            return new int[]{min, max};
        } else {
            int amount = Integer.parseInt(range.trim());
            return new int[]{amount, amount};
        }
    }
    
    /**
     * 加载附魔列表
     */
    private static List<EnchantmentData> loadEnchantments(ConfigurationSection section) {
        List<EnchantmentData> enchantments = new ArrayList<>();
        
        // ✅ 直接读取字符串列表（绕过注释问题）
        List<String> enchantList = section.getStringList("possible-enchantments");
        
        for (String enchantString : enchantList) {
            if (enchantString == null || enchantString.trim().isEmpty()) continue;
            
            String[] parts = enchantString.split(":");
            if (parts.length >= 1) {
                try {
                    Enchantment enchantment = Enchantment.getByKey(
                        org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase())
                    );
                    
                    if (enchantment == null) {
                        YinwuRaidPlugin.getInstance().getLogger().warning("未知附魔：" + parts[0]);
                    }
                    
                    if (enchantment != null) {
                        int level;
                        if (parts.length >= 2) {
                            // 有等级范围（如 "3-5"）
                            int[] levelRange = parseAmountRange(parts[1]);
                            level = levelRange[0] + ThreadLocalRandom.current().nextInt(levelRange[1] - levelRange[0] + 1);
                        } else {
                            // 固定等级
                            level = 1;
                        }
                        enchantments.add(new EnchantmentData(enchantment, level));
                    }
                } catch (Exception e) {
                    YinwuRaidPlugin.getInstance().getLogger().warning("加载附魔失败：" + enchantString + " - " + e.getMessage());
                }
            }
        }
        
        return enchantments;
    }
    
    /**
     * 从 List 加载附魔（更安全的方法）
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    private static List<EnchantmentData> loadEnchantmentsFromList(List<?> enchantList) {
        List<EnchantmentData> enchantments = new ArrayList<>();
        
        for (Object obj : enchantList) {
            if (obj instanceof Map) {
                // 如果是 Map 形式（如 {PROTECTION=4}）
                Map<String, Object> enchantMap = (Map<String, Object>) obj;
                for (Map.Entry<String, Object> entry : enchantMap.entrySet()) {
                    try {
                        String enchantName = entry.getKey();
                        Object levelObj = entry.getValue();
                        
                        Enchantment enchantment = Enchantment.getByKey(
                            org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase())
                        );
                        
                        if (enchantment == null) {
                            YinwuRaidPlugin.getInstance().getLogger().warning("未知附魔：" + enchantName);
                        }
                        
                        if (enchantment != null) {
                            int level;
                            if (levelObj instanceof Number) {
                                level = ((Number) levelObj).intValue();
                            } else if (levelObj instanceof String) {
                                int[] range = parseAmountRange((String) levelObj);
                                level = range[0] + ThreadLocalRandom.current().nextInt(range[1] - range[0] + 1);
                            } else {
                                level = 1;
                            }
                            enchantments.add(new EnchantmentData(enchantment, level));
                        }
                    } catch (Exception e) {
                        YinwuRaidPlugin.getInstance().getLogger().warning("加载附魔失败：" + entry.getKey() + " - " + e.getMessage());
                    }
                }
            } else if (obj instanceof String) {
                // 如果是字符串形式（如 "PROTECTION:4"）
                String enchantString = (String) obj;
                String[] parts = enchantString.split(":");
                if (parts.length >= 1) {
                    try {
                        Enchantment enchantment = Enchantment.getByKey(
                            org.bukkit.NamespacedKey.minecraft(parts[0].toLowerCase())
                        );
                        
                        if (enchantment == null) {
                            YinwuRaidPlugin.getInstance().getLogger().warning("未知附魔：" + parts[0]);
                        }
                        
                        if (enchantment != null) {
                            int level;
                            if (parts.length >= 2) {
                                int[] levelRange = parseAmountRange(parts[1]);
                                level = levelRange[0] + ThreadLocalRandom.current().nextInt(levelRange[1] - levelRange[0] + 1);
                            } else {
                                level = 1;
                            }
                            enchantments.add(new EnchantmentData(enchantment, level));
                        }
                    } catch (Exception e) {
                        YinwuRaidPlugin.getInstance().getLogger().warning("加载附魔失败：" + enchantString + " - " + e.getMessage());
                    }
                }
            }
        }
        
        return enchantments;
    }
    
    /**
     * ✅ 加载不可升级的附魔列表
     */
    private static List<org.bukkit.enchantments.Enchantment> loadExcludedEnchantments(ConfigurationSection section) {
        List<org.bukkit.enchantments.Enchantment> excluded = new ArrayList<>();
        
        List<String> excludedList = section.getStringList("excluded-enchantments");
        for (String enchantName : excludedList) {
            if (enchantName == null || enchantName.trim().isEmpty()) continue;
            
            try {
                Enchantment enchantment = Enchantment.getByKey(
                    org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase().trim())
                );
                
                if (enchantment == null) {
                    YinwuRaidPlugin.getInstance().getLogger().warning("未知排除附魔：" + enchantName);
                }
                
                if (enchantment != null) {
                    excluded.add(enchantment);
                }
            } catch (Exception e) {
                YinwuRaidPlugin.getInstance().getLogger().warning("加载排除附魔失败：" + enchantName + " - " + e.getMessage());
            }
        }
        
        return excluded;
    }
    
    /**
     * ✅ 从 Map 加载不可升级的附魔列表
     */
    @SuppressWarnings({"unchecked", "deprecation"})
    private static List<org.bukkit.enchantments.Enchantment> loadExcludedEnchantmentsFromMap(Map<String, Object> map) {
        List<org.bukkit.enchantments.Enchantment> excluded = new ArrayList<>();
        
        Object excludedObj = map.get("excluded-enchantments");
        if (excludedObj instanceof List) {
            List<?> excludedList = (List<?>) excludedObj;
            for (Object obj : excludedList) {
                if (obj instanceof String) {
                    String enchantName = (String) obj;
                    try {
                        Enchantment enchantment = Enchantment.getByKey(
                            org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase().trim())
                        );
                        
                        if (enchantment == null) {
                            YinwuRaidPlugin.getInstance().getLogger().warning("未知排除附魔：" + enchantName);
                        }
                        
                        if (enchantment != null) {
                            excluded.add(enchantment);
                        }
                    } catch (Exception e) {
                        YinwuRaidPlugin.getInstance().getLogger().warning("加载排除附魔失败：" + enchantName + " - " + e.getMessage());
                    }
                }
            }
        }
        
        return excluded;
    }
    
    /**
     * 附魔数据
     */
    public static class EnchantmentData {
        public final Enchantment enchantment;
        public final int level;
        
        public EnchantmentData(Enchantment enchantment, int level) {
            this.enchantment = enchantment;
            this.level = level;
        }
    }
}