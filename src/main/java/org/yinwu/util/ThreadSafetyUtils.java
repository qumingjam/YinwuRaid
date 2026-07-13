package org.yinwu.util;

/**
 * 线程安全工具类
 * 提供区域线程检查功能
 */
public class ThreadSafetyUtils {
    
    /**
     * 检查当前是否在指定位置所属的区域线程中
     * Paper 单线程模式下始终返回 true
     */
    public static boolean isInRegionThread(org.bukkit.Location location) {
        return org.bukkit.Bukkit.isOwnedByCurrentRegion(location);
    }
    
    /**
     * 断言当前在区域线程中
     */
    public static void assertInRegionThread(String operation, org.bukkit.Location location) {
        if (!isInRegionThread(location)) {
            throw new IllegalStateException("操作 '" + operation + "' 必须在区域线程中执行！");
        }
    }
}
