package org.yinwu.reward;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 村庄英雄奖励管理器
 * 根据村民职业和英雄等级管理奖励配置
 */
public class VillagerRewardManager {
    private final YinwuRaidPlugin plugin;
    private final Map<String, Map<String, List<RewardEntry>>> professionRewards = new HashMap<>();
    
    public VillagerRewardManager(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        loadRewards();
    }
    
    /**
     * 加载奖励配置
     */
    private void loadRewards() {
        // ✅ 新的模块化配置系统：从 rewards/ 目录加载
        File rewardsDir = new File(plugin.getDataFolder(), "rewards");
        
        if (!rewardsDir.exists()) {
            plugin.getLogger().warning("§e⚠ 未找到 rewards/ 目录，尝试从 config.yml 加载（兼容模式）");
            loadRewardsFromConfig();
            return;
        }
        
        plugin.getLogger().info("§a✓ 使用模块化配置系统，从 rewards/ 目录加载...");
        
        // 遍历 rewards/ 目录下的所有 .yml 文件
        File[] yamlFiles = rewardsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        
        if (yamlFiles == null || yamlFiles.length == 0) {
            plugin.getLogger().warning("§e⚠ rewards/ 目录下没有找到配置文件");
            return;
        }
        
        int loadedProfessions = 0;
        
        for (File yamlFile : yamlFiles) {
            try {
                String fileName = yamlFile.getName();
                
                // 跳过全局配置文件
                if (fileName.equals("config.yml")) {
                    continue;
                }
                
                // 提取职业名（文件名去掉 .yml）
                String profession = fileName.substring(0, fileName.length() - 4);
                
                plugin.getLogger().fine("§a✓ 加载职业配置: " + profession);
                
                // 加载该职业的配置文件
                YamlConfiguration config = YamlConfiguration.loadConfiguration(yamlFile);
                ConfigurationSection professionSection = config.getConfigurationSection(profession);
                
                if (professionSection == null) {
                    plugin.getLogger().warning("§e⚠ 文件 " + fileName + " 中未找到 '" + profession + "' 配置节点");
                    plugin.getLogger().warning("§e  文件中的根节点：" + String.join(", ", config.getKeys(false)));
                    continue;
                }
                
                Map<String, List<RewardEntry>> tierMap = new HashMap<>();
                
                // 遍历该职业的所有等级配置
                for (String tier : professionSection.getKeys(false)) {
                    boolean enabled = professionSection.getBoolean(tier + ".enabled", true);
                    
                    if (!enabled) continue;
                    
                    List<RewardEntry> rewards = new ArrayList<>();
                    
                    // ✅ 直接使用 getList 获取 rewards 列表
                    List<?> rewardList = professionSection.getList(tier + ".rewards");
                    
                    if (rewardList != null) {
                        for (int i = 0; i < rewardList.size(); i++) {
                            try {
                                Object obj = rewardList.get(i);
                                if (obj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> rewardMap = (Map<String, Object>) obj;
                                    
                                    RewardEntry entry = RewardEntry.fromMap(rewardMap);
                                    entry.professionType = profession.toLowerCase();
                                    rewards.add(entry);
                                }
                            } catch (Exception e) {
                                plugin.getLogger().severe("§c✗ 加载奖励配置失败：索引 " + i);
                                plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c  错误详情", e);
                            }
                        }
                    }
                    
                    tierMap.put(tier, rewards);
                }
                
                professionRewards.put(profession.toLowerCase(), tierMap);
                loadedProfessions++;
                
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 加载文件 " + yamlFile.getName() + " 失败", e);
            }
        }
        
        plugin.getLogger().info("§a✓ 成功加载 " + loadedProfessions + " 个职业配置");
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [VillagerRewardManager] 已加载 " + loadedProfessions + " 个职业: " + String.join(", ", professionRewards.keySet()));
        }
    }
    
    /**
     * 从 config.yml 加载奖励配置（兼容旧版本）
     */
    private void loadRewardsFromConfig() {
        // ✅ 使用 ConfigManager 从 config.yml 中读取 rewards 配置
        ConfigManager configManager = plugin.getConfigManager();
        if (!configManager.getBukkitConfig().contains("rewards")) {
            plugin.getLogger().severe("§c✗ config.yml 中不存在 rewards 配置节点！");
            return;
        }
        
        plugin.getLogger().fine("§a✓ 从 config.yml 加载 rewards 配置");
        
        // 获取 rewards 配置节
        org.bukkit.configuration.ConfigurationSection rewardsSection = configManager.getBukkitConfig().getConfigurationSection("rewards");
        
        if (rewardsSection == null) {
            plugin.getLogger().severe("§c✗ rewards 配置节为空！");
            return;
        }
        
        // 遍历所有职业配置
        for (String profession : rewardsSection.getKeys(false)) {
            Map<String, List<RewardEntry>> tierMap = new HashMap<>();
            ConfigurationSection professionSection = rewardsSection.getConfigurationSection(profession);
            
            if (professionSection == null) continue;
            
            // 遍历该职业的所有等级配置
            for (String tier : professionSection.getKeys(false)) {
                boolean enabled = professionSection.getBoolean(tier + ".enabled", true);
                
                if (!enabled) continue;
                
                List<RewardEntry> rewards = new ArrayList<>();
                
                // ✅ 直接使用 getList 获取 rewards 列表
                List<?> rewardList = professionSection.getList(tier + ".rewards");
                
                if (rewardList != null) {
                    for (int i = 0; i < rewardList.size(); i++) {
                        try {
                            Object obj = rewardList.get(i);
                            if (obj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> rewardMap = (Map<String, Object>) obj;
                                
                                RewardEntry entry = RewardEntry.fromMap(rewardMap);
                                entry.professionType = profession.toLowerCase();
                                rewards.add(entry);
                            }
                        } catch (Exception e) {
                            plugin.getLogger().severe("§c✗ 加载奖励配置失败：索引 " + i);
                            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c  错误详情", e);
                        }
                    }
                }
                
                tierMap.put(tier, rewards);
            }
            
            professionRewards.put(profession.toLowerCase(), tierMap);
        }
    }
    
    /**
     * 根据村民职业和英雄等级选择奖励
     * 
     * @param villager 村民
     * @param heroLevel 英雄等级（6-10，6=彩蛋级）
     * @return 奖励物品，如果未找到则返回 null
     */
    public ItemStack selectReward(Villager villager, int heroLevel) {
        // ✅ 支持 6 级（彩蛋）到 10 级
        if (heroLevel < 6 || heroLevel > 10) {
            return null;
        }
        
        // 获取村民职业
        String profession = getProfessionName(villager);
        
        // ✅ 调试日志：显示村民职业信息
        plugin.getLogger().fine(String.format("§e✦ 村民职业：%s，英雄等级：%d", profession, heroLevel));
        
        // 尝试获取该职业的奖励配置
        Map<String, List<RewardEntry>> tierMap = professionRewards.get(profession.toLowerCase());
        
        // 如果没有该职业的配置，使用默认配置
        if (tierMap == null) {
            plugin.getLogger().fine("§e⚠ 未找到职业 " + profession + " 的配置，使用默认配置");
            tierMap = professionRewards.get("default");
        }
        
        if (tierMap == null) {
            plugin.getLogger().warning("§c✗ 没有默认奖励配置！请检查 rewards/default.yml 文件是否存在且格式正确");
            plugin.getLogger().warning("§c  已加载的职业配置：" + String.join(", ", professionRewards.keySet()));
            return null;
        }
        
        String tierKey = "villager-hero-" + heroLevel;
        List<RewardEntry> rewards = tierMap.get(tierKey);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [VillagerRewardManager] 选择奖励: 职业=" + profession + ", 英雄等级=" + heroLevel + ", tierKey=" + tierKey + ", 奖励条目数=" + (rewards != null ? rewards.size() : 0));
        }
        
        if (rewards == null || rewards.isEmpty()) {
            plugin.getLogger().warning("§e⚠ 职业 " + profession + " 在 " + tierKey + " 没有奖励配置");
            return null;
        }
        
        return selectWeightedReward(rewards, heroLevel);
    }
    
    /**
     * 加权随机选择奖励
     */
    private ItemStack selectWeightedReward(List<RewardEntry> rewards, int doomLevel) {
        // 计算总权重
        double totalWeight = rewards.stream()
            .mapToDouble(r -> r.chance)
            .sum();
        
        if (totalWeight <= 0) {
            return null;
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [VillagerRewardManager] 加权选择: 总权重=" + totalWeight);
        }
        
        // 随机选择一个权重值
        double random = ThreadLocalRandom.current().nextDouble() * totalWeight;
        
        // 按权重选择奖励
        for (RewardEntry entry : rewards) {
            random -= entry.chance;
            if (random <= 0) {
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [VillagerRewardManager] 选中奖励: material=" + entry.material + ", 权重=" + entry.chance);
                }
                return entry.createItem(doomLevel);
            }
        }
        
        // 理论上不会到这里
        return null;
    }
    
    /**
     * 获取村民的职业名称
     */
    private String getProfessionName(Villager villager) {
        try {
            // 获取村民职业
            Villager.Profession profession = villager.getProfession();
            
            if (profession == null || profession == Villager.Profession.NONE) {
                return "default";
            }
            
            // ✅ 使用 getKey().getKey() 代替 name()（兼容 Folia 1.21+）
            return profession.getKey().getKey().toLowerCase();
            
        } catch (Exception e) {
            plugin.getLogger().warning("§e⚠ 获取村民职业失败：" + e.getMessage());
            return "default";
        }
    }
    
    /**
     * 重新加载配置
     */
    public void reload() {
        professionRewards.clear();
        loadRewards();
    }
}