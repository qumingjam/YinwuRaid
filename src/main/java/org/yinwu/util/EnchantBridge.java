package org.yinwu.util;

import net.yinwu.lib.api.YinwuServiceBridge;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * YinwuEnchant 联动反射桥。
 *
 * 各 Yinwu 插件各自 shade YinwuPluginLib，接口 Class 由不同 ClassLoader 加载，
 * ServicesManager 按 Class 精确匹配会失效。这里通过 {@link YinwuServiceBridge}
 * 从 YinwuEnchant 插件的 ClassLoader 加载接口类拿到服务实例，用反射调用附魔书生成方法。
 */
public final class EnchantBridge {

    /** 附魔实例快照（id + level），与 EnchantAPI.EnchantmentInstance 解耦 */
    public record EnchantLevel(String id, int level) {}

    private static Object provider;      // EnchantAPIImpl 实例（反射访问）
    private static Method mIds;
    private static Method mMaxLevel;
    private static Method mCreateBook;
    private static Method mGetEnchantments;
    private static boolean available = false;

    private EnchantBridge() {}

    /** 初始化：从 YinwuEnchant 插件取 EnchantAPI 服务实例并缓存方法引用。插件未加载时静默跳过。 */
    public static void init() {
        available = false;
        provider = null;
        mIds = mMaxLevel = mCreateBook = mGetEnchantments = null;
        try {
            provider = YinwuServiceBridge.getProvider("YinwuEnchant", "net.yinwu.lib.api.EnchantAPI");
            if (provider == null) return;

            mIds = provider.getClass().getMethod("getEnchantmentIds");
            mMaxLevel = provider.getClass().getMethod("getMaxLevel", String.class);
            mCreateBook = provider.getClass().getMethod("createEnchantedBook", String.class, int.class);
            mGetEnchantments = provider.getClass().getMethod("getEnchantments", ItemStack.class);

            available = true;
        } catch (Exception e) {
            available = false;
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

    /** 获取物品上的自定义附魔列表（反射读取 EnchantmentInstance record） */
    public static List<EnchantLevel> getEnchantments(ItemStack item) {
        List<EnchantLevel> result = new ArrayList<>();
        try {
            Object list = mGetEnchantments.invoke(provider, item);
            if (!(list instanceof List<?> raw)) return result;
            for (Object o : raw) {
                if (o == null) continue;
                Method idMethod = o.getClass().getMethod("id");
                Method levelMethod = o.getClass().getMethod("level");
                result.add(new EnchantLevel((String) idMethod.invoke(o), (Integer) levelMethod.invoke(o)));
            }
        } catch (Exception e) {
            return List.of();
        }
        return result;
    }
}
