package org.yinwu.beacon;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.util.PluginLogger;

import java.util.*;

/**
 * 附魔规则管理器
 * 负责管理灾厄强化系统中的附魔规则和互斥逻辑
 * 支持从配置文件动态加载和热重载
 */
public class EnchantmentRuleManager {
    
    private final YinwuRaidPlugin plugin;
    private final PluginLogger logger;
    
    // ✅ 从配置加载的附魔规则 - 改为实例变量
    private Set<Enchantment> toolCommon = new HashSet<>();
    private Set<Enchantment> pickaxeExclusive = new HashSet<>();
    private Set<Enchantment> shovelExclusive = new HashSet<>();
    private Set<Enchantment> axeAllowed = new HashSet<>();
    private Set<Enchantment> hoeAllowed = new HashSet<>();
    private Set<Enchantment> weaponCommon = new HashSet<>();
    private Set<Enchantment> bowAllowed = new HashSet<>();
    private Set<Enchantment> crossbowAllowed = new HashSet<>();
    private Set<Enchantment> tridentAllowed = new HashSet<>();
    private Set<Enchantment> fishingRodAllowed = new HashSet<>();
    private Set<Enchantment> shieldAllowed = new HashSet<>();
    private Set<Enchantment> elytraAllowed = new HashSet<>();
    private Set<Enchantment> armorGeneric = new HashSet<>();
    private Set<Enchantment> helmetExclusive = new HashSet<>();
    private Set<Enchantment> bootsExclusive = new HashSet<>();
    
    // ✅ 互斥附魔规则
    private List<Set<Enchantment>> mutuallyExclusiveGroups = new ArrayList<>();
    
    // 单例实例（用于向后兼容）
    private static EnchantmentRuleManager instance;
    
    /**
     * 构造函数：初始化附魔规则管理器
     * 
     * @param pluginInstance 插件实例
     */
    public EnchantmentRuleManager(YinwuRaidPlugin pluginInstance) {
        this.plugin = pluginInstance;
        this.logger = new PluginLogger(pluginInstance);
        loadRulesFromConfig();
    }
    
    /**
     * ✅ 初始化单例实例（用于向后兼容）
     */
    public static void initialize(YinwuRaidPlugin pluginInstance) {
        instance = new EnchantmentRuleManager(pluginInstance);
    }
    
    /**
     * 获取单例实例
     */
    public static EnchantmentRuleManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("EnchantmentRuleManager 未初始化！");
        }
        return instance;
    }
    
    /**
     * ✅ 重新加载配置（热重载）
     */
    public void reload() {
        logger.info("§e正在重新加载附魔规则...");
        loadRulesFromConfig();
        logger.success("附魔规则已重新加载");
    }
    
    /**
     * ✅ 从 ConfigManager 加载附魔规则
     */
    private void loadRulesFromConfig() {
        if (plugin == null) {
            throw new IllegalStateException("EnchantmentRuleManager 未初始化！");
        }
        
        // ✅ 使用 ConfigManager 获取强化配置
        org.yinwu.config.ConfigManager configManager = plugin.getConfigManager();
        org.yinwu.config.ConfigManager.EnhancementConfig enhancementConfig = configManager.getEnhancementConfig();
        
        if (enhancementConfig == null || enhancementConfig.getLimits() == null) {
            plugin.getLogger().warning("§e⚠ 未找到 enhancement.rules 配置，使用默认规则");
            useDefaultRules();
            return;
        }
        
        try {
            // ✅ 从 rewards/config.yml 加载附魔规则
            java.io.File rewardsConfigFile = new java.io.File(plugin.getDataFolder(), "rewards/config.yml");
            org.bukkit.configuration.file.YamlConfiguration rewardsConfig = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(rewardsConfigFile);
            
            org.bukkit.configuration.ConfigurationSection rulesSection = 
                rewardsConfig.getConfigurationSection("enhancement.rules");
            
            if (rulesSection == null) {
                plugin.getLogger().warning("§e⚠ 未找到 enhancement.rules 配置，使用默认规则");
                useDefaultRules();
                return;
            }
            
            // 加载各类附魔规则
            toolCommon = loadEnchantmentSet(rulesSection, "tool-common");
            pickaxeExclusive = loadEnchantmentSet(rulesSection, "pickaxe-exclusive");
            shovelExclusive = loadEnchantmentSet(rulesSection, "shovel-exclusive");
            axeAllowed = loadEnchantmentSet(rulesSection, "axe-allowed");
            hoeAllowed = loadEnchantmentSet(rulesSection, "hoe-allowed");
            weaponCommon = loadEnchantmentSet(rulesSection, "weapon-common");
            bowAllowed = loadEnchantmentSet(rulesSection, "bow-allowed");
            crossbowAllowed = loadEnchantmentSet(rulesSection, "crossbow-allowed");
            tridentAllowed = loadEnchantmentSet(rulesSection, "trident-allowed");
            fishingRodAllowed = loadEnchantmentSet(rulesSection, "fishing-rod-allowed");
            shieldAllowed = loadEnchantmentSet(rulesSection, "shield-allowed");
            elytraAllowed = loadEnchantmentSet(rulesSection, "elytra-allowed");
            armorGeneric = loadEnchantmentSet(rulesSection, "armor-generic");
            helmetExclusive = loadEnchantmentSet(rulesSection, "helmet-exclusive");
            bootsExclusive = loadEnchantmentSet(rulesSection, "boots-exclusive");
            
            // 加载互斥规则
            loadMutuallyExclusiveRules(rulesSection);
            
            logger.fine(String.format("§a✓ 成功加载附魔规则：%d 个工具通用, %d 个镐子专属, %d 个武器通用等",
                toolCommon.size(), pickaxeExclusive.size(), weaponCommon.size()));
            
        } catch (Exception e) {
            logger.error("§c✗ 加载附魔规则配置失败：" + e.getMessage(), e);
            useDefaultRules();
        }
    }
    
    /**
     * ✅ 从配置中加载附魔集合
     */
    private Set<Enchantment> loadEnchantmentSet(org.bukkit.configuration.ConfigurationSection section, String path) {
        List<String> enchantNames = section.getStringList(path);
        Set<Enchantment> enchantSet = new HashSet<>();
        
        for (String name : enchantNames) {
            try {
                // 支持命名空间格式（如 minecraft:efficiency）或简单格式（如 efficiency）
                String keyName = name.contains(":") ? name.split(":")[1] : name;
                Enchantment enchant = Enchantment.getByKey(org.bukkit.NamespacedKey.fromString("minecraft:" + keyName));
                
                if (enchant != null) {
                    enchantSet.add(enchant);
                } else {
                    plugin.getLogger().warning(String.format("§e⚠ 无效的附魔名称：%s（路径：%s）", name, path));
                }
            } catch (Exception e) {
                logger.warning(String.format("§e⚠ 加载附魔失败：%s - %s", name, e.getMessage()));
            }
        }
        
        return enchantSet;
    }
    
    /**
     * ✅ 加载互斥附魔规则
     */
    private void loadMutuallyExclusiveRules(org.bukkit.configuration.ConfigurationSection rulesSection) {
        mutuallyExclusiveGroups.clear();
        
        org.bukkit.configuration.ConfigurationSection exclusiveSection = 
            rulesSection.getConfigurationSection("mutually-exclusive");
        
        if (exclusiveSection == null) {
            plugin.getLogger().warning("§e⚠ 未找到互斥附魔配置");
            return;
        }
        
        for (String groupKey : exclusiveSection.getKeys(false)) {
            List<String> enchantNames = exclusiveSection.getStringList(groupKey);
            Set<Enchantment> group = new HashSet<>();
            
            for (String name : enchantNames) {
                try {
                    String keyName = name.contains(":") ? name.split(":")[1] : name;
                    Enchantment enchant = Enchantment.getByKey(org.bukkit.NamespacedKey.fromString("minecraft:" + keyName));
                    
                    if (enchant != null) {
                        group.add(enchant);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning(String.format("§e⚠ 加载互斥附魔失败：%s", name));
                }
            }
            
            if (!group.isEmpty()) {
                mutuallyExclusiveGroups.add(group);
            }
        }
        
        logger.fine(String.format("§a✓ 加载了 %d 个互斥附魔组", mutuallyExclusiveGroups.size()));
    }
    
    /**
     * ✅ 使用默认规则（后备方案）
     */
    private void useDefaultRules() {
        toolCommon = Set.of(
            Enchantment.EFFICIENCY,
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        pickaxeExclusive = Set.of(
            Enchantment.FORTUNE,
            Enchantment.SILK_TOUCH
        );
        
        shovelExclusive = Set.of(
            Enchantment.FORTUNE,
            Enchantment.SILK_TOUCH
        );
        
        axeAllowed = Set.of(
            Enchantment.SHARPNESS,
            Enchantment.SMITE,
            Enchantment.BANE_OF_ARTHROPODS,
            Enchantment.KNOCKBACK,
            Enchantment.FIRE_ASPECT,
            Enchantment.LOOTING
        );
        
        hoeAllowed = Set.of(
            Enchantment.FORTUNE,
            Enchantment.SILK_TOUCH
        );
        
        weaponCommon = Set.of(
            Enchantment.SHARPNESS,
            Enchantment.SMITE,
            Enchantment.BANE_OF_ARTHROPODS,
            Enchantment.KNOCKBACK,
            Enchantment.FIRE_ASPECT,
            Enchantment.LOOTING,
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        bowAllowed = Set.of(
            Enchantment.POWER,
            Enchantment.PUNCH,
            Enchantment.FLAME,
            Enchantment.INFINITY,
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        crossbowAllowed = Set.of(
            Enchantment.QUICK_CHARGE,
            Enchantment.MULTISHOT,
            Enchantment.PIERCING,
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        tridentAllowed = Set.of(
            Enchantment.SHARPNESS,
            Enchantment.LOYALTY,
            Enchantment.RIPTIDE,
            Enchantment.CHANNELING,
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        fishingRodAllowed = Set.of(
            Enchantment.LURE,
            Enchantment.LUCK_OF_THE_SEA,
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        shieldAllowed = Set.of(
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        elytraAllowed = Set.of(
            Enchantment.UNBREAKING,
            Enchantment.MENDING
        );
        
        armorGeneric = Set.of(
            Enchantment.PROTECTION,
            Enchantment.FIRE_PROTECTION,
            Enchantment.PROJECTILE_PROTECTION,
            Enchantment.BLAST_PROTECTION,
            Enchantment.UNBREAKING,
            Enchantment.MENDING,
            Enchantment.THORNS
        );
        
        helmetExclusive = Set.of(
            Enchantment.RESPIRATION,
            Enchantment.AQUA_AFFINITY
        );
        
        bootsExclusive = Set.of(
            Enchantment.FROST_WALKER,
            Enchantment.DEPTH_STRIDER,
            Enchantment.FEATHER_FALLING
        );
        
        // 默认互斥规则
        mutuallyExclusiveGroups = Arrays.asList(
            Set.of(Enchantment.FORTUNE, Enchantment.SILK_TOUCH),
            Set.of(Enchantment.FROST_WALKER, Enchantment.DEPTH_STRIDER)
        );
        
        logger.warning("§e  使用默认附魔规则");
    }
    
    /**
     * 检查附魔是否可以应用到物品上
     * 
     * @param item 物品
     * @param enchantment 附魔
     * @return true 如果可以应用
     */
    public boolean canApplyEnchantment(ItemStack item, Enchantment enchantment) {
        Material type = item.getType();
        boolean result;
        
        if (isPickaxe(type)) {
            result = toolCommon.contains(enchantment) || pickaxeExclusive.contains(enchantment);
        } else if (isShovel(type)) {
            result = toolCommon.contains(enchantment) || shovelExclusive.contains(enchantment);
        } else if (isAxe(type)) {
            result = toolCommon.contains(enchantment) || axeAllowed.contains(enchantment);
        } else if (isHoe(type)) {
            result = toolCommon.contains(enchantment) || hoeAllowed.contains(enchantment);
        } else if (isSword(type)) {
            result = weaponCommon.contains(enchantment);
        } else if (type == Material.BOW) {
            result = bowAllowed.contains(enchantment);
        } else if (type == Material.CROSSBOW) {
            result = crossbowAllowed.contains(enchantment);
        } else if (type == Material.TRIDENT) {
            result = tridentAllowed.contains(enchantment);
        } else if (type == Material.FISHING_ROD) {
            result = fishingRodAllowed.contains(enchantment);
        } else if (type == Material.SHIELD) {
            result = shieldAllowed.contains(enchantment);
        } else if (type == Material.ELYTRA) {
            result = elytraAllowed.contains(enchantment);
        } else if (isArmor(type)) {
            if (armorGeneric.contains(enchantment)) {
                result = true;
            } else if (isHelmet(type) && helmetExclusive.contains(enchantment)) {
                result = true;
            } else if (isBoots(type) && bootsExclusive.contains(enchantment)) {
                result = true;
            } else {
                result = false;
            }
        } else {
            result = false;
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [EnchantmentRuleManager] canApplyEnchantment: 物品类型=" + type.name() + ", 附魔=" + enchantment.getKey().getKey() + ", 结果=" + result);
        }
        return result;
    }
    
    /**
     * 检查附魔是否与现有附魔冲突
     * 
     * @param item 物品
     * @param newEnchant 新附魔
     * @param existingEnchants 现有附魔
     * @return true 如果存在冲突
     */
    public boolean hasConflict(ItemStack item, Enchantment newEnchant, Map<Enchantment, Integer> existingEnchants) {
        // ✅ 检查所有互斥组
        for (Set<Enchantment> exclusiveGroup : mutuallyExclusiveGroups) {
            if (exclusiveGroup.contains(newEnchant)) {
                for (Enchantment existing : existingEnchants.keySet()) {
                    if (exclusiveGroup.contains(existing) && !existing.equals(newEnchant)) {
                        if (plugin.getConfigManager().isDebugEnabled()) {
                            plugin.getLogger().info("§e[DEBUG] [EnchantmentRuleManager] hasConflict: 物品类型=" + item.getType().name() + ", 新附魔=" + newEnchant.getKey().getKey() + ", 冲突附魔=" + existing.getKey().getKey() + ", 结果=冲突（互斥组）");
                        }
                        return true;
                    }
                }
            }
        }
        
        // ✅ 特殊处理：激流与忠诚/引雷互斥
        if (newEnchant == Enchantment.RIPTIDE) {
            if (existingEnchants.containsKey(Enchantment.LOYALTY) || 
                existingEnchants.containsKey(Enchantment.CHANNELING)) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [EnchantmentRuleManager] hasConflict: 物品类型=" + item.getType().name() + ", 新附魔=" + newEnchant.getKey().getKey() + ", 结果=冲突（激流与忠诚/引雷互斥）");
                }
                return true;
            }
        }
        
        if (newEnchant == Enchantment.LOYALTY || newEnchant == Enchantment.CHANNELING) {
            if (existingEnchants.containsKey(Enchantment.RIPTIDE)) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [EnchantmentRuleManager] hasConflict: 物品类型=" + item.getType().name() + ", 新附魔=" + newEnchant.getKey().getKey() + ", 结果=冲突（与激流互斥）");
                }
                return true;
            }
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [EnchantmentRuleManager] hasConflict: 物品类型=" + item.getType().name() + ", 新附魔=" + newEnchant.getKey().getKey() + ", 结果=无冲突");
        }
        return false;
    }
    
    private static boolean isPickaxe(Material type) {
        return type.name().endsWith("_PICKAXE");
    }
    
    private static boolean isShovel(Material type) {
        return type.name().endsWith("_SHOVEL");
    }
    
    private static boolean isAxe(Material type) {
        return type.name().endsWith("_AXE");
    }
    
    private static boolean isHoe(Material type) {
        return type.name().endsWith("_HOE");
    }
    
    private static boolean isSword(Material type) {
        return type.name().endsWith("_SWORD");
    }
    
    private static boolean isArmor(Material type) {
        return type.name().endsWith("_HELMET") ||
               type.name().endsWith("_CHESTPLATE") ||
               type.name().endsWith("_LEGGINGS") ||
               type.name().endsWith("_BOOTS");
    }
    
    private static boolean isHelmet(Material type) {
        return type.name().endsWith("_HELMET");
    }
    
    private static boolean isBoots(Material type) {
        return type.name().endsWith("_BOOTS");
    }
}