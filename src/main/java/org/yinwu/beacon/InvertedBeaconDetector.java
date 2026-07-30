package org.yinwu.beacon;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.util.ThreadSafetyUtils;

/**
 * 灾厄信标检测器
 * 用于检测灾厄信标结构并验证其合法性
 *
 * <p>✅ 信标结构已从 config.yml 配置化，支持自定义：</p>
 * <ul>
 *     <li>容器底座 - 最底层 (y-1) - 可配置类型（木桶/箱子等）</li>
 *     <li>信标方块 - 第 0 层 (y+0)</li>
 *     <li>第 1-4 层 - 从配置文件 beacon.layers 动态加载</li>
 * </ul>
 *
 * <p>✅ 默认结构（游戏内从下到上）：</p>
 * <ul>
 *     <li>木桶/箱子 - 最底层 (y-1) - 必需</li>
 *     <li>信标方块 - 第 0 层 (y+0)</li>
 *     <li>3x3 铁块 - 第 1 层 (y+1)</li>
 *     <li>5x5 金块 - 第 2 层 (y+2)</li>
 *     <li>7x7 钻石块 - 第 3 层 (y+3)</li>
 *     <li>9x9 下界合金块 - 最上层 (y+4)</li>
 * </ul>
 *
 * <p>✅ 等级判定规则（从下往上检查）：</p>
 * <ul>
 *     <li>等级 0: 不是信标方块或没有容器底座</li>
 *     <li>等级 1: 完成第 1 层</li>
 *     <li>等级 2: 完成第 1+2 层</li>
 *     <li>等级 3: 完成第 1+2+3 层</li>
 *     <li>等级 4: 完整结构（所有层）</li>
 *     <li>等级 6: 彩蛋级（4层全是绿宝石块）</li>
 * </ul>
 */
public class InvertedBeaconDetector {

    private final YinwuRaidPlugin plugin;

    // 记录最后检测到的信标位置
    private Location lastDetectedBeacon;

    // ✅ 从配置加载的信标层级
    private Layer[] layers;

    // ✅ 从配置加载的容器类型
    private java.util.Set<Material> containerTypes;

    // ✅ 是否需要容器底座
    private boolean requireContainer;

    public InvertedBeaconDetector(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
        loadLayersFromConfig();
        loadDetectionConfig();
    }

    /**
     * ✅ 从 ConfigManager 加载信标层级结构
     */
    private void loadLayersFromConfig() {
        try {
            ConfigManager configManager = plugin.getConfigManager();
            org.yinwu.config.BeaconConfig beaconConfig = configManager.getBeaconConfig();

            if (beaconConfig == null || beaconConfig.getLayers() == null || beaconConfig.getLayers().isEmpty()) {
                plugin.getLogger().warning("§e⚠ 未找到信标层级配置，使用默认结构");
                useDefaultLayers();
                return;
            }

            // 动态加载层级（支持 1-4 层）
            java.util.List<Layer> layerList = new java.util.ArrayList<>();

            for (java.util.Map.Entry<Integer, org.yinwu.config.LayerConfig> entry : beaconConfig.getLayers().entrySet()) {
                int level = entry.getKey();
                org.yinwu.config.LayerConfig layerConfig = entry.getValue();

                String materialName = layerConfig.getMaterial();
                int size = layerConfig.getSize();
                int offset = layerConfig.getOffset();

                Material material;
                try {
                    material = Material.valueOf(materialName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().severe(String.format("§c✗ 无效的材料名称：%s，使用默认值", materialName));
                    material = Material.NETHERITE_BLOCK;
                }

                layerList.add(new Layer(material, size, offset));
                plugin.getLogger().fine(String.format("§a✓ 加载第 %d 层：%dx%d %s (Y+%d)",
                    level, size, size, materialName, offset));
            }

            if (layerList.isEmpty()) {
                plugin.getLogger().warning("§e⚠ 未加载任何层级配置，使用默认结构");
                useDefaultLayers();
            } else {
                this.layers = layerList.toArray(new Layer[0]);
                plugin.getLogger().fine(String.format("§a✓ 成功加载 %d 层信标结构", layers.length));
            }

        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 加载信标层级配置失败", e);
            useDefaultLayers();
        }
    }

    /**
     * ✅ 使用默认层级结构（后备方案）
     * 注意：顺序必须与 config.yml 一致！1=最底层，4=最顶层
     */
    private void useDefaultLayers() {
        this.layers = new Layer[] {
            new Layer(Material.IRON_BLOCK, 3, 1),        // 第1层：3x3 铁块
            new Layer(Material.GOLD_BLOCK, 5, 2),        // 第2层：5x5 金块
            new Layer(Material.DIAMOND_BLOCK, 7, 3),     // 第3层：7x7 钻石块
            new Layer(Material.NETHERITE_BLOCK, 9, 4)    // 第4层：9x9 下界合金块
        };
        plugin.getLogger().info("§e  使用默认信标结构（1=铁块 3x3, 4=下界合金块 9x9）");
    }

    /**
     * ✅ 从 ConfigManager 加载信标判定配置
     */
    private void loadDetectionConfig() {
        try {
            ConfigManager configManager = plugin.getConfigManager();
            org.yinwu.config.BeaconConfig beaconConfig = configManager.getBeaconConfig();

            if (beaconConfig == null) {
                plugin.getLogger().warning("§e⚠ 未找到信标判定配置，使用默认值");
                useDefaultDetectionConfig();
                return;
            }

            // 加载容器类型
            java.util.List<String> containerTypeNames = beaconConfig.getContainerTypes();
            if (containerTypeNames == null || containerTypeNames.isEmpty()) {
                plugin.getLogger().warning("§e⚠ 未配置容器类型，使用默认值");
                useDefaultDetectionConfig();
                return;
            }

            java.util.Set<Material> tmp = new java.util.HashSet<>();
            for (String typeName : containerTypeNames) {
                try {
                    Material material = Material.valueOf(typeName.toUpperCase());
                    tmp.add(material);
                    plugin.getLogger().fine(String.format("§a✓ 加载容器类型：%s", typeName));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().severe(String.format("§c✗ 无效的材料名称：%s，已跳过", typeName));
                }
            }

            this.containerTypes = java.util.Collections.unmodifiableSet(tmp);
            if (this.containerTypes.isEmpty()) {
                plugin.getLogger().warning("§e⚠ 没有有效的容器类型，使用默认值");
                useDefaultDetectionConfig();
                return;
            }

            // 加载是否需要容器
            this.requireContainer = beaconConfig.isRequireContainer();

            plugin.getLogger().fine(String.format("§a✓ 成功加载信标判定配置：需要容器=%b, 容器类型数=%d",
                this.requireContainer, this.containerTypes.size()));

        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 加载信标判定配置失败", e);
            useDefaultDetectionConfig();
        }
    }

    /**
     * ✅ 使用默认判定配置（后备方案）
     */
    private void useDefaultDetectionConfig() {
        java.util.Set<Material> tmp = new java.util.HashSet<>();
        tmp.add(Material.BARREL);
        tmp.add(Material.CHEST);
        tmp.add(Material.TRAPPED_CHEST);
        this.containerTypes = java.util.Collections.unmodifiableSet(tmp);
        this.requireContainer = true;
        plugin.getLogger().info("§e  使用默认信标判定配置");
    }

    /**
     * 检查指定位置是否为灾厄信标（只要是信标方块即可）
     */
    public boolean isInvertedBeacon(Location beaconLocation) {
        boolean result = beaconLocation.getBlock().getType() == Material.BEACON;
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] isInvertedBeacon - 位置: " + beaconLocation.toString() + ", 结果: " + result);
        }
        return result;
    }

    /**
     * 检查信标正下方是否有容器
     *
     * @param beaconLocation 信标位置
     * @return true 如果有容器或不需要容器，false 否则
     */
    public boolean hasContainerBelowBeacon(Location beaconLocation) {
        // ✅ 如果配置为不需要容器，直接返回 true
        if (!requireContainer) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] hasContainerBelowBeacon - 不需要容器，返回 true");
            }
            return true;
        }

        // 空值检查
        if (beaconLocation == null || beaconLocation.getWorld() == null) {
            plugin.getLogger().warning("§c⚠ 信标位置或其世界为空！");
            return false;
        }

        // 获取信标正下方的方块 (Y-1)
        Location containerLocation = beaconLocation.clone().subtract(0, 1, 0);
        Block containerBlock = containerLocation.getBlock();
        Material type = containerBlock.getType();

        // ✅ 使用从配置加载的容器类型
        boolean hasContainer = containerTypes != null && containerTypes.contains(type);

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] hasContainerBelowBeacon - 位置: " + containerLocation.toString() + ", 方块类型: " + type.name() + ", 是容器: " + hasContainer);
        }

        if (!hasContainer) {
            plugin.getLogger().fine(String.format("§c⚠ 信标正下方没有有效容器！位置: %s, 类型: %s",
                containerLocation.toString(), type.name()));
        }

        return hasContainer;
    }

    /**
     * 检查指定层是否完整
     */
    private boolean checkLayer(Location beaconLocation, Layer layer) {
        World world = beaconLocation.getWorld();
        if (world == null) return false;

        int baseY = beaconLocation.getBlockY() + layer.depthOffset();
        int halfSize = layer.size() / 2;

        try {
            for (int x = -halfSize; x <= halfSize; x++) {
                for (int z = -halfSize; z <= halfSize; z++) {
                    Block block = world.getBlockAt(
                        beaconLocation.getBlockX() + x,
                        baseY,
                        beaconLocation.getBlockZ() + z
                    );

                    if (!block.getType().equals(layer.material())) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning(String.format("§e⚠ 检测信标层级 %d 时出错：%s", layer.depthOffset(), e.getMessage()));
            return false;
        }

        return true;
    }

    /**
     * 获取灾厄信标的等级
     *
     * ⚠️ 注意：此方法必须在信标所在的区域线程中调用！
     *
     * @return 信标等级 (1-4) 或彩蛋级 (6)
     */
    public int getBeaconLevel(Location beaconLocation) {
        // ✅ 使用 ThreadSafetyUtils 检查区域线程，避免依赖异常捕获来检测线程错误
        if (beaconLocation.getWorld() != null && !ThreadSafetyUtils.isInRegionThread(beaconLocation)) {
            plugin.getLogger().severe(String.format("§c✗ getBeaconLevel 未在正确的区域线程中调用！位置：%s", beaconLocation.toString()));
            plugin.getLogger().severe("§e请在调用前使用 RegionScheduler 包裹");
            return 0;
        }

        // 首先检查是否是信标方块
        if (beaconLocation.getBlock().getType() != Material.BEACON) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] getBeaconLevel - 不是信标方块，返回等级 0");
            }
            return 0;
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] getBeaconLevel - 是信标方块，检查容器底座");
        }

        if (!hasContainerBelowBeacon(beaconLocation)) {
            plugin.getLogger().fine("§c⚠ 信标结构不完整：缺少容器底座！");
            return 0;
        }

        this.lastDetectedBeacon = beaconLocation.clone();

        // ✅ 使用从配置加载的层级
        if (layers == null || layers.length == 0) {
            plugin.getLogger().warning("§c✗ 信标层级未初始化！");
            return 0;
        }

        // 普通信标等级检测（单次遍历）
        int level = 0;

        for (int i = 0; i < layers.length; i++) {
            boolean layerComplete = checkLayer(beaconLocation, layers[i]);

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] getBeaconLevel - 第 " + (i + 1) + " 层检查: " + (layerComplete ? "完成" : "不完整"));
            }

            if (!layerComplete) {
                break; // 某一层不完整，不再继续检查上层
            }

            level = i + 1;
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] getBeaconLevel - 普通等级结果: " + level);
        }

        // ✅ 仅在确认全部 4 层完整后才进行彩蛋检测，避免无效遍历
        if (level >= layers.length && checkEasterEggBeacon(beaconLocation)) {
            plugin.getLogger().info(String.format("§d✨ 发现彩蛋级信标！位置：%s", beaconLocation.toString()));
            return 6; // ✅ 返回等级 6 表示彩蛋级
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] getBeaconLevel - 最终等级: " + level);
        }

        return level;
    }

    /**
     * ✅ 检测是否为彩蛋级信标（4层全是绿宝石块）
     *
     * @param beaconLocation 信标位置
     * @return true=彩蛋级，false=普通信标
     */
    private boolean checkEasterEggBeacon(Location beaconLocation) {
        World world = beaconLocation.getWorld();
        if (world == null) return false;

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] checkEasterEggBeacon - 开始检查彩蛋信标，位置: " + beaconLocation.toString());
        }

        // 直接检查实际方块是否为 EMERALD_BLOCK，不依赖配置
        for (int i = 0; i < layers.length; i++) {
            Layer layer = layers[i];
            int baseY = beaconLocation.getBlockY() + layer.depthOffset();
            int halfSize = layer.size() / 2;

            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] checkEasterEggBeacon - 检查第 " + (i + 1) + " 层，Y偏移: " + layer.depthOffset());
            }

            // 检查该层的每个方块是否都是 EMERALD_BLOCK
            try {
                for (int x = -halfSize; x <= halfSize; x++) {
                    for (int z = -halfSize; z <= halfSize; z++) {
                        Block block = world.getBlockAt(
                            beaconLocation.getBlockX() + x,
                            baseY,
                            beaconLocation.getBlockZ() + z
                        );

                        // 如果不是绿宝石块，直接返回 false
                        if (block.getType() != Material.EMERALD_BLOCK) {
                            if (plugin.getConfigManager().isDebugEnabled()) {
                                plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] checkEasterEggBeacon - 第 " + (i + 1) + " 层找到非绿宝石块: " + block.getType().name() + "，非彩蛋信标");
                            }
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning(String.format("§e⚠ 检测彩蛋信标层级 %d 时出错：%s", layer.depthOffset(), e.getMessage()));
                return false;
            }
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [InvertedBeaconDetector] checkEasterEggBeacon - 4层均为绿宝石块，是彩蛋信标！");
        }

        // 4层都是绿宝石块且都完整
        return true;
    }

    /**
     * 获取最后检测到的信标位置
     */
    public Location getLastDetectedBeacon() {
        return lastDetectedBeacon;
    }

    /**
     * ✅ 获取信标层级配置（供 GUI 使用）
     *
     * @return 层级数组，如果未初始化则返回默认值
     */
    public Layer[] getLayers() {
        if (layers == null || layers.length == 0) {
            return new Layer[] {
                new Layer(Material.NETHERITE_BLOCK, 3, 1),
                new Layer(Material.DIAMOND_BLOCK, 5, 2),
                new Layer(Material.GOLD_BLOCK, 7, 3),
                new Layer(Material.IRON_BLOCK, 9, 4)
            };
        }
        return layers;
    }

    /**
     * ✅ 获取容器类型集合（供外部使用）
     *
     * @return 容器类型集合，如果未初始化则返回 null
     */
    public java.util.Set<Material> getContainerTypes() {
        return containerTypes;
    }

    /**
     * ✅ 获取是否需要容器底座的配置
     *
     * @return true=需要容器，false=不需要容器
     */
    public boolean isRequireContainer() {
        return requireContainer;
    }

    /**
     * 记录层信息的内部类
     *
     * @param material 层级材料类型
     * @param size 层级尺寸（边长，必须是奇数）
     * @param depthOffset Y 坐标偏移量（从信标 A 的 Y+1 开始计算的相对偏移）
     */
    private record Layer(Material material, int size, int depthOffset) {
    }
}
