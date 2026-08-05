package org.yinwu.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.yinwu.YinwuRaidPlugin;

import java.lang.reflect.Method;
import java.util.List;

/**
 * YinwuEnchant 联动反射桥。
 *
 * 各 Yinwu 插件各自 shade YinwuPluginLib，接口 Class 由不同 ClassLoader 加载，
 * ServicesManager 按 Class 精确匹配会失效。这里通过 YinwuEnchant 插件的 ClassLoader
 * 加载接口类拿到注册用的同一个 Class，再取服务实例，用反射调用附魔书生成方法。
 */
public final class EnchantBridge {

    private static Object provider;      // EnchantAPIImpl 实例（反射访问）
    private static Method mIds;
    private static Method mMaxLevel;
    private static Method mCreateBook;
    private static boolean available = false;

    private EnchantBridge() {}

    /** 初始化：从 YinwuEnchant 插件取 EnchantAPI 服务实例并缓存方法引用。插件未加载时静默跳过。 */
    public static void init() {
        available = false;
        provider = null;
        mIds = mMaxLevel = mCreateBook = null;
        YinwuRaidPlugin plugin = YinwuRaidPlugin.getInstance();

        Plugin ench = Bukkit.getPluginManager().getPlugin("YinwuEnchant");
        if (ench == null) {
            plugin.getLogger().warning("[EnchantBridge] getPlugin(YinwuEnchant)=null");
            return;
        }

        try {
            ClassLoader cl = ench.getClass().getClassLoader();
            Class<?> api = Class.forName("net.yinwu.lib.api.EnchantAPI", true, cl);

            @SuppressWarnings({"unchecked", "rawtypes"})
            var reg = Bukkit.getServicesManager().getRegistration((Class) api);
            if (reg == null) {
                plugin.getLogger().warning("[EnchantBridge] getRegistration null for " + api.getName());
                return;
            }
            provider = reg.getProvider();
            if (provider == null) {
                plugin.getLogger().warning("[EnchantBridge] provider null");
                return;
            }

            mIds = api.getMethod("getEnchantmentIds");
            mMaxLevel = api.getMethod("getMaxLevel", String.class);
            mCreateBook = api.getMethod("createEnchantedBook", String.class, int.class);

            available = true;
        } catch (Exception e) {
            plugin.getLogger().warning("[EnchantBridge] init 异常: " + e);
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    @SuppressWarnings("unchecked")
    public static List<String> getEnchantmentIds() {
        try {
            return (List<String>) mIds.invoke(provider);
        } catch (Exception e) {
            return List.of();
        }
    }

    public static int getMaxLevel(String enchantId) {
        try {
            return (Integer) mMaxLevel.invoke(provider, enchantId);
        } catch (Exception e) {
            return 1;
        }
    }

    public static ItemStack createEnchantedBook(String enchantId, int level) {
        try {
            return (ItemStack) mCreateBook.invoke(provider, enchantId, level);
        } catch (Exception e) {
            return null;
        }
    }
}
