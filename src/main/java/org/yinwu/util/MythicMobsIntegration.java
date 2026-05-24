package org.yinwu.util;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.yinwu.YinwuRaidPlugin;

import java.lang.reflect.Method;

/**
 * MythicMobs 集成工具类
 * 用于生成和管理 MythicMobs 自定义生物
 */
public class MythicMobsIntegration {
    
    private final YinwuRaidPlugin plugin;
    private boolean mythicMobsAvailable = false;
    private Object mythicMobsAPI;
    private Method getMobManagerMethod;
    private Method spawnMethod;
    private Method getEntityMethod;
    
    public MythicMobsIntegration(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        checkMythicMobs();
    }
    
    /**
     * 检查 MythicMobs 是否可用
     */
    private void checkMythicMobs() {
        try {
            // 检查 MythicMobs 插件是否存在
            if (plugin.getServer().getPluginManager().getPlugin("MythicMobs") == null) {
                plugin.getLogger().info("§e⚠ MythicMobs 未安装，将只使用原版生物");
                mythicMobsAvailable = false;
                return;
            }
            
            // 尝试获取 MythicMobs API
            Class<?> apiClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            mythicMobsAPI = getInstanceMethod.invoke(null);
            
            // 缓存 getMobManager 方法引用
            getMobManagerMethod = apiClass.getMethod("getMobManager");
            Object mobManager = getMobManagerMethod.invoke(mythicMobsAPI);
            
            // 缓存 spawnMob 方法引用
            Class<?> mobManagerClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMobManager");
            spawnMethod = mobManagerClass.getMethod("spawnMob", String.class, Location.class);
            
            // 缓存 ActiveMob.getEntity 方法引用
            Class<?> activeMobClass = Class.forName("io.lumine.mythic.core.mobs.ActiveMob");
            getEntityMethod = activeMobClass.getMethod("getEntity");
            
            mythicMobsAvailable = true;
            plugin.getLogger().info("§a✓ MythicMobs 集成成功！可以使用自定义生物");
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("§e⚠ MythicMobs API 类未找到，可能版本不兼容");
            mythicMobsAvailable = false;
        } catch (Exception e) {
            plugin.getLogger().warning("§e⚠ MythicMobs 集成失败：" + e.getMessage());
            mythicMobsAvailable = false;
        }
    }
    
    /**
     * 检查是否是 MythicMobs 生物ID
     * 
     * @param mobId 生物ID
     * @return 是否是 MythicMobs 生物
     */
    public boolean isMythicMob(String mobId) {
        return mobId != null && mobId.startsWith("mythicmob:");
    }
    
    /**
     * 从配置字符串中提取 MythicMobs ID
     * 
     * @param configString 配置字符串（如 "mythicmob:ShadowAssassin"）
     * @return MythicMobs ID（如 "ShadowAssassin"）
     */
    public String extractMythicMobId(String configString) {
        if (configString == null || !configString.startsWith("mythicmob:")) {
            return null;
        }
        return configString.substring(10); // 去掉 "mythicmob:" 前缀
    }
    
    /**
     * 生成 MythicMobs 生物
     * 
     * @param mythicMobId MythicMobs 生物ID
     * @param location 生成位置
     * @return 生成的实体，失败返回 null
     */
    public LivingEntity spawnMythicMob(String mythicMobId, Location location) {
        if (!mythicMobsAvailable) {
            plugin.getLogger().warning("§c✗ MythicMobs 不可用，无法生成生物：" + mythicMobId);
            return null;
        }
        
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("§c✗ 生成位置无效");
            return null;
        }
        
        try {
            // 使用缓存的 Method 引用调用 MythicMobs API 生成生物
            Object mobManager = getMobManagerMethod.invoke(mythicMobsAPI);
            Object activeMob = spawnMethod.invoke(mobManager, mythicMobId, location);
            
            if (activeMob == null) {
                plugin.getLogger().warning("§c✗ MythicMobs 生物生成失败：" + mythicMobId);
                return null;
            }
            
            // 使用缓存的 Method 引用获取 Bukkit 实体
            Entity bukkitEntity = (Entity) getEntityMethod.invoke(activeMob);
            
            if (bukkitEntity instanceof LivingEntity) {
                plugin.getLogger().fine(String.format("§a✓ 成功生成 MythicMobs 生物：%s", mythicMobId));
                return (LivingEntity) bukkitEntity;
            } else {
                plugin.getLogger().warning("§c✗ 生成的实体不是 LivingEntity：" + mythicMobId);
                return null;
            }
            
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 生成 MythicMobs 生物时出错", e);
            return null;
        }
    }
    
    /**
     * 检查 MythicMobs 是否可用
     * 
     * @return 是否可用
     */
    public boolean isAvailable() {
        return mythicMobsAvailable;
    }
}
