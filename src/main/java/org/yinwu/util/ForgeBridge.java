package org.yinwu.util;

import net.yinwu.lib.api.YinwuServiceBridge;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

/**
 * YinwuForge 联动反射桥（与 EnchantBridge 同思路）。
 *
 * 各 Yinwu 插件各自 shade YinwuPluginLib，ServicesManager 按 Class 精确匹配会失效，
 * 这里通过 YinwuForge 插件的 ClassLoader 加载接口拿到服务实例，用反射调用。
 */
public final class ForgeBridge {

    private static Object provider;
    private static Method mIsForgeItem;
    private static Method mGetForgeLevel;
    private static boolean available = false;

    private ForgeBridge() {}

    /** 初始化：从 YinwuForge 插件取 ForgeAPI 服务实例并缓存方法引用。插件未加载时静默跳过。 */
    public static void init() {
        available = false;
        provider = null;
        mIsForgeItem = mGetForgeLevel = null;
        try {
            provider = YinwuServiceBridge.getProvider("YinwuForge", "net.yinwu.lib.api.ForgeAPI");
            if (provider == null) return;
            mIsForgeItem = provider.getClass().getMethod("isForgeItem", ItemStack.class);
            mGetForgeLevel = provider.getClass().getMethod("getForgeLevel", ItemStack.class);
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static boolean isForgeItem(ItemStack item) {
        try {
            return (Boolean) mIsForgeItem.invoke(provider, item);
        } catch (Exception e) {
            return false;
        }
    }

    public static int getForgeLevel(ItemStack item) {
        try {
            return (Integer) mGetForgeLevel.invoke(provider, item);
        } catch (Exception e) {
            return 0;
        }
    }
}
