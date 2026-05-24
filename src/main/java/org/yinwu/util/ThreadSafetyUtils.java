package org.yinwu.util;

/**
 * 线程安全工具类
 * 提供 Folia 区域线程检查和通用线程安全操作
 */
public class ThreadSafetyUtils {
    
    private static final String REGION_THREAD_CHECK_PROPERTY = "paper.threaded-regions";
    private static final Boolean FOLIA_AVAILABLE = checkFoliaAvailability();
    
    /**
     * 检测 Folia API 是否可用（类加载时执行一次）
     */
    private static boolean checkFoliaAvailability() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 检查当前是否在区域线程中
     * 
     * @param location 用于检查区域线程的位置
     * @return true 如果在区域线程或非 Folia 环境，false 否则
     */
    public static boolean isInRegionThread(org.bukkit.Location location) {
        // 非 Folia 环境默认安全
        if (!FOLIA_AVAILABLE) {
            return true;
        }
        
        // 使用 Bukkit API 检查当前线程是否拥有指定位置所属的区域
        return org.bukkit.Bukkit.isOwnedByCurrentRegion(location);
    }
    
    /**
     * 检查是否为 Folia 环境
     * 
     * @return true 如果是 Folia 环境
     */
    public static boolean isFoliaEnvironment() {
        return Boolean.getBoolean(REGION_THREAD_CHECK_PROPERTY);
    }
    
    /**
     * 断言当前在区域线程中，如果不在则抛出异常
     * 
     * @param operation 正在执行的操作描述
     * @param location 用于检查区域线程的位置
     * @throws IllegalStateException 如果当前不在区域线程中
     */
    public static void assertInRegionThread(String operation, org.bukkit.Location location) {
        if (!isInRegionThread(location)) {
            throw new IllegalStateException("操作 '" + operation + "' 必须在区域线程中执行！");
        }
    }
}