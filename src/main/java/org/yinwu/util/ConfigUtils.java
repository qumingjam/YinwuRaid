package org.yinwu.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 配置读取工具类
 * 提供统一的配置读取方法和默认值处理
 */
public class ConfigUtils {
    
    /**
     * 读取整数配置，带默认值和范围检查
     * 
     * @param config 配置对象
     * @param path 配置路径
     * @param defaultValue 默认值
     * @param min 最小值
     * @param max 最大值
     * @return 限制在范围内的整数值
     */
    public static int getInt(Configuration config, String path, int defaultValue, int min, int max) {
        int value = config.getInt(path, defaultValue);
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * 读取双精度配置，带默认值和范围检查
     * 
     * @param config 配置对象
     * @param path 配置路径
     * @param defaultValue 默认值
     * @param min 最小值
     * @param max 最大值
     * @return 限制在范围内的双精度值
     */
    public static double getDouble(Configuration config, String path, double defaultValue, double min, double max) {
        double value = config.getDouble(path, defaultValue);
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * 安全获取配置节，如果不存在则抛出异常
     * 
     * @param config 配置对象
     * @param path 配置路径
     * @return 配置节对象
     * @throws IllegalArgumentException 如果配置节不存在
     */
    public static ConfigurationSection getSection(Configuration config, String path) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            throw new IllegalArgumentException("配置节 '" + path + "' 不存在！");
        }
        return section;
    }
    
    /**
     * 加载材料列表（从字符串列表转换为 Material 集合）
     * 
     * @param section 配置节
     * @param path 配置路径
     * @return 有效的 Material 集合
     */
    public static Set<Material> loadMaterialSet(ConfigurationSection section, String path) {
        List<String> materialNames = section.getStringList(path);
        Set<Material> materials = new HashSet<>();
        
        for (String name : materialNames) {
            try {
                Material material = Material.valueOf(name.toUpperCase());
                materials.add(material);
            } catch (IllegalArgumentException e) {
                // 记录警告但不中断
                Bukkit.getLogger().warning("[YinwuRaid] 无效的材料名称: " + name);
            }
        }
        
        return materials;
    }
    
    /**
     * 读取布尔配置，带默认值
     * 
     * @param config 配置对象
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 布尔值
     */
    public static boolean getBoolean(Configuration config, String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }
    
    /**
     * 读取字符串配置，带默认值
     * 
     * @param config 配置对象
     * @param path 配置路径
     * @param defaultValue 默认值
     * @return 字符串值
     */
    public static String getString(Configuration config, String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }
    
    /**
     * 读取长整型配置，带默认值和范围检查
     * 
     * @param config 配置对象
     * @param path 配置路径
     * @param defaultValue 默认值
     * @param min 最小值
     * @param max 最大值
     * @return 限制在范围内的长整型值
     */
    public static long getLong(Configuration config, String path, long defaultValue, long min, long max) {
        long value = config.getLong(path, defaultValue);
        return Math.max(min, Math.min(max, value));
    }
}
