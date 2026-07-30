package org.yinwu.enums;

import org.bukkit.Bukkit;
import org.yinwu.YinwuRaidPlugin;

/**
 * 灾厄信标等级枚举
 * 提供类型安全的信标等级表示和判断
 */
public enum BeaconLevel {

    // 未激活状态
    LEVEL_0(0, "未激活", ""),

    // 基础等级（1-4级）
    LEVEL_1(1, "基础", "铁块"),
    LEVEL_2(2, "进阶", "金块"),
    LEVEL_3(3, "高级", "钻石块"),
    LEVEL_4(4, "大师", "下界合金块"),

    // 彩蛋等级
    EASTER_EGG(6, "彩蛋", "绿宝石块");

    private final int level;
    private final String displayName;
    private final String materialName;

    /**
     * 构造函数
     *
     * @param level 等级数值
     * @param displayName 显示名称
     * @param materialName 该等级的标志性材料名称
     */
    BeaconLevel(int level, String displayName, String materialName) {
        this.level = level;
        this.displayName = displayName;
        this.materialName = materialName;
    }

    /**
     * 获取等级数值
     *
     * @return 等级数值
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取显示名称
     *
     * @return 显示名称（如"基础"、"进阶"等）
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取标志性材料名称
     *
     * @return 材料名称（如"铁块"、"金块"等）
     */
    public String getMaterialName() {
        return materialName;
    }

    /**
     * 从整数转换为枚举
     *
     * @param level 等级数值
     * @return 对应的枚举值，如果找不到则返回 LEVEL_0
     */
    public static BeaconLevel fromInt(int level) {
        BeaconLevel result = LEVEL_0;
        for (BeaconLevel bl : values()) {
            if (bl.level == level) {
                result = bl;
                break;
            }
        }

        YinwuRaidPlugin plugin;
        try { plugin = YinwuRaidPlugin.getInstance(); } catch (Exception e) { plugin = null; }
        if (plugin != null && plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconLevel] 等级查询: input=" + level + ", result=" + result.name());
        }

        return result;
    }

    /**
     * 判断是否为彩蛋等级
     *
     * @return true 如果是彩蛋等级
     */
    public boolean isEasterEgg() {
        return this == EASTER_EGG;
    }

    /**
     * 判断是否为有效等级（已激活）
     *
     * @return true 如果等级大于 0
     */
    public boolean isValid() {
        return this != LEVEL_0;
    }

    /**
     * 判断是否为基础等级（1-4级）
     *
     * @return true 如果是基础等级
     */
    public boolean isNormal() {
        return this == LEVEL_1 || this == LEVEL_2 || this == LEVEL_3 || this == LEVEL_4;
    }

    /**
     * 获取带格式的显示文本
     *
     * @return 格式化的显示文本（如"§4大师"）
     */
    public String getFormattedName() {
        if (isEasterEgg()) {
            return "§d" + displayName;
        }

        switch (this) {
            case LEVEL_1:
                return "§7" + displayName;
            case LEVEL_2:
                return "§6" + displayName;
            case LEVEL_3:
                return "§b" + displayName;
            case LEVEL_4:
                return "§4" + displayName;
            default:
                return "§8" + displayName;
        }
    }

    /**
     * 获取灾厄等级映射（默认计算公式）
     *
     * @return 对应的灾厄等级
     */
    public int getDefaultDoomLevel() {
        if (isEasterEgg()) {
            return 15; // 彩蛋级对应灾厄等级 15
        }
        return level + 6; // 普通等级 = 信标等级 + 6
    }
}
