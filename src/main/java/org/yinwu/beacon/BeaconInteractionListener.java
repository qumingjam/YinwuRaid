package org.yinwu.beacon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.effect.DoomEffectManager;
import org.yinwu.config.ConfigManager;
import org.yinwu.enums.BeaconLevel;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灾厄信标交互监听器
 * 处理玩家与灾厄信标的交互
 */
public class BeaconInteractionListener implements Listener, InventoryHolder {
    
    private final YinwuRaidPlugin plugin;
    private final InvertedBeaconDetector detector;
    private final DoomEffectManager effectManager;
    private final DisasterSeedManager seedManager;
    private final ConfigManager configManager;
    
    // GUI 固定槽位配置（非层级相关）
    private static final int BEACON_SLOT = 40;
    private static final int BARREL_SLOT = 49;
    private static final int INTRO_BOOK_SLOT = 50;
    private static final int STAR_SLOT = 44;
    private static final int ACTIVATION_SLOT = 53;
    private static final int STAR_INFO_BOOK_SLOT = 35;
    private static final int ANVIL_SWITCH_SLOT = 45;
    
    // 显示槽位（不可交互）
    private static final int DISPLAY_TOOL_SLOT = 20;
    private static final int DISPLAY_BOOK_SLOT = 22;
    private static final int DISPLAY_PREVIEW_SLOT = 24;
    
    // 操作槽位（可交互）
    private static final int INPUT_TOOL_SLOT = 29;
    private static final int INPUT_BOOK_SLOT = 31;
    private static final int OUTPUT_SLOT = 33;
    
    // 基础名称，等级会动态添加
    private static final String GUI_TITLE_BASE = "§4§l灾厄信标";
    private static final String ENCHANT_GUI_TITLE = "§6§l灾厄强化";
    
    // ✅ 魔法数字常量
    private static final int EASTER_EGG_LEVEL = 6;           // 彩蛋级信标等级
    
    // 用于跟踪当前打开的 GUI 类型
    private final java.util.Set<java.util.UUID> enchantGUIPlayers = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    
    // 每个玩家的显示索引（UUID -> 索引）- 使用 ConcurrentHashMap 保证线程安全
    private final Map<UUID, Integer> playerDisplayIndexes = new ConcurrentHashMap<>();
    
    // 每个玩家打开信标GUI时的信标等级（UUID -> beaconLevel）- 使用 ConcurrentHashMap 保证线程安全
    private final Map<UUID, Integer> playerBeaconLevels = new ConcurrentHashMap<>();
    
    // 自动切换显示 - 使用 ConcurrentHashMap 保证线程安全
    private final Map<UUID, io.papermc.paper.threadedregions.scheduler.ScheduledTask> displayUpdateTasks = new ConcurrentHashMap<>();
    private static final Material[] DISPLAY_ITEMS = {
        Material.NETHERITE_PICKAXE,
        Material.NETHERITE_SWORD,
        Material.NETHERITE_CHESTPLATE,
        Material.NETHERITE_HELMET,
        Material.BOW,
        Material.TRIDENT
    };
    
    public BeaconInteractionListener(YinwuRaidPlugin plugin, InvertedBeaconDetector detector, DoomEffectManager effectManager) {
        this.plugin = plugin;
        this.detector = detector;
        this.effectManager = effectManager;
        this.seedManager = new DisasterSeedManager(plugin);
        this.configManager = plugin.getConfigManager();
    }
    
    @Override
    public @org.jetbrains.annotations.Nullable Inventory getInventory() {
        // 此类实现 InventoryHolder 仅用于标识 GUI 所有者
        // Bukkit 会自动将创建的 Inventory 与此监听器关联
        // 无需通过此方法返回实际的 Inventory 对象
        return null;
    }
    
    /**
     * 创建灾厄信标 GUI
     * 
     * @param beaconLevel 信标等级（1-4 或 6）
     * @return 配置好的 Inventory 对象
     */
    private Inventory createBeaconGUI(int beaconLevel) {
        String guiTitle = GUI_TITLE_BASE + "  -  " + (BeaconLevel.fromInt(beaconLevel).isEasterEgg() ? "§d彩蛋" : beaconLevel) + "级";
        Inventory gui = Bukkit.createInventory(this, 54, guiTitle);
        
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }
        
        // 彩蛋级特殊处理：所有层都显示绿宝石块
        boolean isEasterEgg = (beaconLevel == EASTER_EGG_LEVEL);
        
        // 从 ConfigManager 读取层级信息并动态生成GUI展示
        ConfigManager.BeaconConfig beaconConfig = configManager.getBeaconConfig();
        if (beaconConfig != null && beaconConfig.getLayers() != null) {
            // 按等级从高到低遍历（倒序，等级高的在上面）
            java.util.List<Integer> keys = new java.util.ArrayList<>(beaconConfig.getLayers().keySet());
            keys.sort((a, b) -> b - a);
            
            // 从第0行开始放置（最上面是最高等级）
            int currentRow = 0;
            for (Integer level : keys) {
                ConfigManager.LayerConfig layerConfig = beaconConfig.getLayers().get(level);
                if (layerConfig != null) {
                    String materialName = layerConfig.getMaterial();
                    int size = layerConfig.getSize();
                    
                    // 只有当信标等级 >= 当前层级时才显示
                    if (beaconLevel >= level || isEasterEgg) {
                        Material material;
                        
                        // 彩蛋级：所有层都使用 EMERALD_BLOCK
                        if (isEasterEgg) {
                            material = Material.EMERALD_BLOCK;
                        } else {
                            try {
                                material = Material.valueOf(materialName.toUpperCase());
                            } catch (IllegalArgumentException e) {
                                material = Material.BARRIER;
                            }
                        }
                        
                        // 计算该层在GUI中的槽位（居中显示）
                        int[] slots = calculateLayerSlots(size, currentRow);
                        String chineseName = isEasterEgg ? "§d绿宝石块" : getMaterialChineseName(materialName);
                        
                        for (int slot : slots) {
                            gui.setItem(slot, createMaterialItem(material, "§f" + chineseName));
                        }
                    }
                    
                    currentRow++; // 下一层放在下一行
                }
            }
        }
        
        gui.setItem(BEACON_SLOT, createMaterialItem(Material.BEACON, "§f信标"));
        gui.setItem(BARREL_SLOT, createMaterialItem(Material.BARREL, "§f木桶"));
        
        ItemStack starInfoBook = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta starInfoMeta = starInfoBook.getItemMeta();
        if (starInfoMeta != null) {
            starInfoMeta.displayName(Component.text("激活说明")
                .color(TextColor.color(0xFFAA00))
                .decorate(TextDecoration.BOLD));
            
            java.util.List<String> containerTypes = beaconConfig != null ? beaconConfig.getContainerTypes() : java.util.Arrays.asList("BARREL", "CHEST", "TRAPPED_CHEST");
            boolean requireContainer = beaconConfig != null ? beaconConfig.isRequireContainer() : true;
            
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7━━━━━━━━━━━━━━━━");
            
            if (!isEasterEgg) {
                ConfigManager.ActivationConfig activationConfig = beaconConfig != null ? beaconConfig.getActivationConfig() : null;
                String activationDisplayName = activationConfig != null ? activationConfig.getDisplayName() : "下界之星";
                int activationAmount = activationConfig != null ? activationConfig.getAmount() : 1;
                                    
                lore.add("§f将 §b" + activationDisplayName + " §f放入");
                lore.add("§f然后点击");
                lore.add("§f绿色染料激活信标");
                
                if (requireContainer && !containerTypes.isEmpty()) {
                    lore.add("");
                    lore.add("§e需要底座容器：");
                    for (String containerType : containerTypes) {
                        String containerName = getMaterialChineseName(containerType);
                        lore.add("§7- " + containerName);
                    }
                } else if (!requireContainer) {
                    lore.add("");
                    lore.add("§e无需底座容器");
                }
                
                lore.add("");
                lore.add("§c消耗：" + activationAmount + " x " + activationDisplayName);
            } else {
                //  彩蛋级：显示神秘提示
                lore.add("§d§l??? 神秘的激活方式 ???");
                lore.add("");
                lore.add("§d寻找特殊的物品");
                lore.add("§d才能唤醒这股力量");
                
                if (requireContainer && !containerTypes.isEmpty()) {
                    lore.add("");
                    lore.add("§e需要底座容器：");
                    for (String containerType : containerTypes) {
                        String containerName = getMaterialChineseName(containerType);
                        lore.add("§7- " + containerName);
                    }
                }
            }
            
            lore.add("§7━━━━━━━━━━━━━━━━");
            starInfoMeta.lore(lore.stream().map(s -> Component.text(org.bukkit.ChatColor.translateAlternateColorCodes('§', s))).toList());
            starInfoBook.setItemMeta(starInfoMeta);
        }
        gui.setItem(STAR_INFO_BOOK_SLOT, starInfoBook);
        
        gui.setItem(STAR_SLOT, null);
        
        // 创建灾厄信标介绍书（从配置读取层级信息）
        ItemStack introBook = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta bookMeta = introBook.getItemMeta();
        if (bookMeta != null) {
            bookMeta.displayName(Component.text("灾厄信标介绍")
                .color(TextColor.color(0xFFAA00))
                .decorate(TextDecoration.BOLD));
            
            // 彩蛋级特殊处理：不显示任何层级信息
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("§7━━━━━━━━━━━━━━━━");
            
            if (!isEasterEgg) {
                // 普通信标：显示层级信息
                if (beaconConfig != null) {
                    java.util.Map<Integer, ConfigManager.LayerConfig> layers = beaconConfig.getLayers();
                    if (layers != null) {
                        for (java.util.Map.Entry<Integer, ConfigManager.LayerConfig> entry : layers.entrySet()) {
                            int level = entry.getKey();
                            ConfigManager.LayerConfig layerConfig = entry.getValue();
                            String materialName = layerConfig.getMaterial();
                            int size = layerConfig.getSize();
                            
                            // 获取材料中文名
                            String chineseName = getMaterialChineseName(materialName);
                            lore.add("§a等级 " + level + " = " + chineseName + "（" + size + "x" + size + "）");
                        }
                    }
                }
            } else {
                //  彩蛋级：显示神秘提示
                lore.add("§d§l??? 神秘的信标结构 ???");
                lore.add("");
                lore.add("§d只有真正的探索者");
                lore.add("§d才能发现它的秘密");
            }
            
            lore.add("§7━━━━━━━━━━━━━━━━");
            bookMeta.lore(lore.stream().map(s -> Component.text(org.bukkit.ChatColor.translateAlternateColorCodes('§', s))).toList());
            introBook.setItemMeta(bookMeta);
        }
        gui.setItem(INTRO_BOOK_SLOT, introBook);
        
        ItemStack greenDye = new ItemStack(Material.GREEN_DYE);
        ItemMeta dyeMeta = greenDye.getItemMeta();
        if (dyeMeta != null) {
            dyeMeta.displayName(Component.text("激活信标")
                .color(TextColor.color(0x55FF55))
                .decorate(TextDecoration.BOLD));
            
            java.util.List<String> dyeLore;
            // ✅ 使用 ConfigManager 获取激活配置
            ConfigManager.ActivationConfig activationConfig = beaconConfig != null ? beaconConfig.getActivationConfig() : null;
            boolean isEasterEggLevel = (beaconLevel == EASTER_EGG_LEVEL);
            
            if (!isEasterEggLevel && activationConfig != null) {
                int activationAmount = activationConfig.getAmount();
                String activationDisplayName = activationConfig.getDisplayName();
                
                dyeLore = Arrays.asList(
                    "§7━━━━━━━━━━━━━━━━",
                    "§f点击激活灾厄信标",
                    "",
                    "§c消耗：" + activationAmount + " x " + activationDisplayName,
                    "§7━━━━━━━━━━━━━━━━"
                );
            } else {
                dyeLore = Arrays.asList(
                    "§7━━━━━━━━━━━━━━━━",
                    "§f点击激活灾厄信标",
                    "",
                    "§d§l??? 神秘的消耗 ???",
                    "§7━━━━━━━━━━━━━━━━"
                );
            }
            
            dyeMeta.lore(dyeLore.stream().map(s -> Component.text(org.bukkit.ChatColor.translateAlternateColorCodes('§', s))).toList());
            greenDye.setItemMeta(dyeMeta);
        }
        gui.setItem(ACTIVATION_SLOT, greenDye);
        
        ItemStack anvilSwitch = new ItemStack(Material.ANVIL);
        ItemMeta anvilMeta = anvilSwitch.getItemMeta();
        if (anvilMeta != null) {
            anvilMeta.displayName(Component.text("灾厄强化")
                .color(TextColor.color(0xFFAA00))
                .decorate(TextDecoration.BOLD));
            
            if (BeaconLevel.fromInt(beaconLevel) == BeaconLevel.LEVEL_4 || BeaconLevel.fromInt(beaconLevel).isEasterEgg()) {
                java.util.List<String> anvilLore = Arrays.asList(
                    "§7━━━━━━━━━━━━━━━━",
                    "§f点击切换到灾厄强化界面",
                    "",
                    "§e可以应用超过原版上限的附魔",
                    "§7━━━━━━━━━━━━━━━━"
                );
                anvilMeta.lore(anvilLore.stream().map(s -> Component.text(org.bukkit.ChatColor.translateAlternateColorCodes('§', s))).toList());
            } else {
                anvilMeta.setLore(Arrays.asList(
                    "§7━━━━━━━━━━━━━━━━",
                    "§c需要 4 级信标",
                    "§c才能解锁此功能",
                    "",
                    "§7当前等级：" + (BeaconLevel.fromInt(beaconLevel).isEasterEgg() ? "§d彩蛋" : String.valueOf(beaconLevel)) + " 级",
                    "§7━━━━━━━━━━━━━━━━"
                ));
                anvilSwitch.setType(Material.BARRIER); // 改为障碍方块表示禁用
            }
            
            anvilSwitch.setItemMeta(anvilMeta);
        }
        gui.setItem(ANVIL_SWITCH_SLOT, anvilSwitch);
        
        return gui;
    }
    
    /**
     * 创建灾厄强化 GUI
     */
    private Inventory createEnchantGUI() {
        Inventory gui = Bukkit.createInventory(this, 54, ENCHANT_GUI_TITLE);
        
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }
        
        gui.setItem(DISPLAY_TOOL_SLOT, createDisplayItem(Material.NETHERITE_PICKAXE, "§b§l示例工具"));
        gui.setItem(DISPLAY_BOOK_SLOT, createDisplayItem(Material.WRITTEN_BOOK, "§d§l灾厄之种"));
        gui.setItem(DISPLAY_PREVIEW_SLOT, createDisplayItem(Material.ANVIL, "§a§l强化预览"));
        
        gui.setItem(INPUT_TOOL_SLOT, null);
        gui.setItem(INPUT_BOOK_SLOT, null);
        gui.setItem(OUTPUT_SLOT, null);
        
        return gui;
    }
    
    /**
     * 创建显示物品
     */
    private ItemStack createDisplayItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 将 § 格式的颜色代码转换为 Adventure Component
            meta.displayName(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(displayName));
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 创建材料展示物品
     */
    private ItemStack createMaterialItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            item.setItemMeta(meta);
        }
        return item;
    }
    
    /**
     * 获取材料的中文名称
     */
    private String getMaterialChineseName(String materialName) {
        switch (materialName.toUpperCase()) {
            case "NETHERITE_BLOCK":
                return "下界合金块";
            case "DIAMOND_BLOCK":
                return "钻石块";
            case "GOLD_BLOCK":
                return "金块";
            case "IRON_BLOCK":
                return "铁块";
            case "BARREL":
                return "木桶";
            case "CHEST":
                return "箱子";
            case "TRAPPED_CHEST":
                return "陷阱箱";
            case "SHULKER_BOX":
                return "潜影盒";
            default:
                // 尝试从 Material 枚举获取
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    return material.name().replace("_", "");
                } catch (IllegalArgumentException e) {
                    return materialName;
                }
        }
    }
    
    /**
     * 根据层级大小和行数计算GUI槽位（居中显示）
     * @param size 层级尺寸（3, 5, 7, 9等）
     * @param row 行数（0-5，对应GUI的第几行）
     * @return 槽位数组
     */
    private int[] calculateLayerSlots(int size, int row) {
        // GUI每行9个槽位，需要居中显示
        int startSlot = (9 - size) / 2 + row * 9;
        int[] slots = new int[size];
        for (int i = 0; i < size; i++) {
            slots[i] = startSlot + i;
        }
        return slots;
    }
    
    /**
     * 检查物品是否为不祥之瓶（彩蛋级专用）
     * 简化逻辑：只要是不祥之瓶就允许
     */
    private boolean isOminousBottleLevel1(ItemStack item) {
        if (item == null) {
            return false;
        }
        
        // 直接检查物品类型
        return item.getType() == Material.OMINOUS_BOTTLE;
    }
    
    /**
     * 发送 ActionBar 消息给玩家
     */
    private void sendActionBar(Player player, String message) {
        if (!player.isOnline()) return;
        
        // ✅ Folia 兼容：在玩家所在区域线程中发送 ActionBar
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;
            try {
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));
                
                // 3秒后清除 ActionBar
                Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), (clearTask) -> {
                    if (player.isOnline()) {
                        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(""));
                    }
                }, 60L);
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 发送 ActionBar 失败：" + e.getMessage());
            }
        });
    }
    
    /**
     * 广播 ActionBar 消息给范围内玩家
     */
    private void broadcastActionBar(Location center, double radius, String message) {
        if (center == null || center.getWorld() == null) return;
        
        Bukkit.getRegionScheduler().run(plugin, center, (task) -> {
            try {
                for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof Player) {
                        sendActionBar((Player) entity, message);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 广播 ActionBar 失败：" + e.getMessage());
            }
        });
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != org.bukkit.Material.BEACON) {
            return;
        }
        
        Player player = event.getPlayer();
        Location beaconLocation = block.getLocation();
        
        Bukkit.getRegionScheduler().run(plugin, beaconLocation, (task) -> {
            handleBeaconInteraction(player, beaconLocation);
        });
    }
    
    private void handleBeaconInteraction(Player player, Location beaconLocation) {
        // ✅ 缓存 BeaconConfig 到局部变量
        ConfigManager.BeaconConfig beaconConfig = configManager.getBeaconConfig();
        
        if (!detector.isInvertedBeacon(beaconLocation)) {
            return;
        }
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 玩家 " + player.getName() + " 右键点击信标，位置：" + beaconLocation.toString());
        }
        
        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            plugin.getLogger().fine("信标交互：" + player.getName());
        }
        
        plugin.getSpecialRaidListener().setBeaconLocation(beaconLocation);
        
        // ✅ 使用 ConfigManager 检查信标是否启用
        if (beaconConfig != null && !beaconConfig.isEnabled()) {
            sendActionBar(player, "§c[系统] §7灾厄信标功能已禁用");
            return;
        }
        
        if (!detector.hasContainerBelowBeacon(beaconLocation)) {
            sendActionBar(player, "§c§l❌ 灾厄信标结构不完整！");
            return;
        }
        
        // ✅ 如果配置为不需要容器，跳过容器检查
        boolean requireContainer = beaconConfig != null ? beaconConfig.isRequireContainer() : true;
        if (requireContainer) {
            Location belowLocation = beaconLocation.clone().subtract(0, 1, 0);
            Material belowMaterial = belowLocation.getBlock().getType();
            boolean isContainer = detector.getContainerTypes() != null && 
                                 detector.getContainerTypes().contains(belowMaterial);
            
            if (!isContainer) {
                plugin.getLogger().fine("检测到信标 B: " + beaconLocation);
                return;
            }
        }
        
        int beaconLevel = detector.getBeaconLevel(beaconLocation);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 玩家 " + player.getName() + " 信标等级: " + beaconLevel + "，位置：" + beaconLocation.toString());
        }
        
        // 保存信标等级到玩家映射中，激活时使用
        playerBeaconLevels.put(player.getUniqueId(), beaconLevel);
        
        Inventory gui = createBeaconGUI(beaconLevel);
        player.openInventory(gui);
        
        if (!BeaconLevel.fromInt(beaconLevel).isValid()) {
            player.sendMessage("§7━━━━━━━━━━━━━━━━");
            player.sendMessage("§c信标结构不完整，无法激活！");
            player.sendMessage("");
            player.sendMessage("§6【激活方法】");
            player.sendMessage("§f1. §b在信标下方 §e放置木桶");
            
            // ✅ 从 ConfigManager 读取所有层级信息
            if (beaconConfig != null && beaconConfig.getLayers() != null) {
                // 按等级排序（从低到高）
                java.util.List<Integer> keys = new java.util.ArrayList<>(beaconConfig.getLayers().keySet());
                keys.sort((a, b) -> a - b);
                
                int step = 2;
                for (Integer level : keys) {
                    ConfigManager.LayerConfig layerConfig = beaconConfig.getLayers().get(level);
                    if (layerConfig != null) {
                        String materialName = layerConfig.getMaterial();
                        int size = layerConfig.getSize();
                        String chineseName = getMaterialChineseName(materialName);
                        player.sendMessage("§f" + step + ". §b在信标上方 §e放置 " + size + "×" + size + " " + chineseName + " §7（激活 " + level + " 级信标）");
                        step++;
                    }
                }
            } else {
                // 配置读取失败，显示默认值
                player.sendMessage("§f2. §b在信标上方 §e放置 3×3 铁块 §7（激活 1 级信标）");
            }
            
            player.sendMessage("§f" + (!BeaconLevel.fromInt(beaconLevel).isValid() ? "3" : "下一步") + ". §e完成后右键信标即可激活");
            player.sendMessage("");
            player.sendMessage("§c未检测到完整的结构层");
            player.sendMessage("§7━━━━━━━━━━━━━━━━");
        }
    }
    
    private void giveDoomEffect(Player player, int beaconLevel) {
        // ✅ 缓存 BeaconConfig 到局部变量
        ConfigManager.BeaconConfig doomBeaconConfig = configManager.getBeaconConfig();
        
        // ✅ 使用 ConfigManager 获取灾厄等级映射
        java.util.Map<Integer, Integer> doomLevels = doomBeaconConfig != null ? doomBeaconConfig.getDoomLevels() : new java.util.HashMap<>();
        int doomLevel = doomLevels.getOrDefault(beaconLevel, beaconLevel + 6);
        int duration = (doomBeaconConfig != null ? doomBeaconConfig.getDoomEffectDuration() : 300) * 20;
        
        effectManager.applyDoomEffect(player, beaconLevel, duration);
        
        // ✅ 彩蛋级特殊效果：绿色视觉覆盖
        if (BeaconLevel.fromInt(beaconLevel).isEasterEgg()) {
            startEasterEggGreenEffect(player, duration);
        }
        
        Location beaconLocation = detector.getLastDetectedBeacon();
        int broadcastRadius = 100;
        
        sendActionBar(player, "§6[灾厄信标] §e你获得了 §4灾厄 §e效果！等级：" + doomLevel);
        
        if (beaconLocation != null) {
            broadcastActionBar(beaconLocation, broadcastRadius, "§6[灾厄信标] §e附近的玩家获得了 §4灾厄 §e效果！等级：" + doomLevel);
        }
    }
    
    /**
     * ✅ 新增：启动彩蛋级绿色视觉效果
     * 
     * @param player 玩家
     * @param duration 持续时间（tick）
     */
    private void startEasterEggGreenEffect(Player player, int duration) {
        UUID playerUuid = player.getUniqueId();
        
        // ✅ 施加夜视效果（让视野更亮）
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.NIGHT_VISION,
            duration,
            0, // I 级
            false, // 不显示粒子
            false, // 不显示图标
            true // 环境效果
        ));
        
        plugin.getLogger().fine(String.format("§d✨ [彩蛋视觉] 已为玩家 %s 启动绿色视觉效果！", player.getName()));
        
        // ✅ 在区域线程中生成一次性绿色粒子爆发
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (regionTask) -> {
            try {
                Location eyeLoc = player.getEyeLocation();
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                
                // 生成一圈绿色粒子（仅一次）
                int particleCount = 20;
                for (int i = 0; i < particleCount; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distance = 1.0 + random.nextDouble() * 2.0; // 1-3 格范围
                    
                    int x = (int) (eyeLoc.getBlockX() + Math.cos(angle) * distance);
                    int z = (int) (eyeLoc.getBlockZ() + Math.sin(angle) * distance);
                    int y = eyeLoc.getBlockY() + random.nextInt(3) - 1;
                    
                    Location particleLoc = new Location(player.getWorld(), x + 0.5, y, z + 0.5);
                    
                    // 生成绿色粒子
                    player.getWorld().spawnParticle(
                        org.bukkit.Particle.DUST,
                        particleLoc,
                        1,
                        0.0f, 0.0f, 0.0f,
                        0.0f,
                        new org.bukkit.Particle.DustOptions(
                            org.bukkit.Color.fromRGB(0, 255, 0),
                            1.0f
                        )
                    );
                }
            } catch (Exception e) {
                plugin.getLogger().warning("§e⚠ 生成绿色粒子失败：" + e.getMessage());
            }
        });
    }
    
    /**
     * 处理库存点击事件
     * 主入口方法，根据槽位类型分发到不同的处理方法
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isValidBeaconGUI(event)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        Inventory gui = event.getInventory();
        int rawSlot = event.getRawSlot();
        
        // 处理背包槽位点击（Shift+点击）
        if (rawSlot >= 54) {
            handleBackpackClick(event, player);
            return;
        }
        
        // 验证是否为可交互槽位
        if (!isInteractableSlot(rawSlot)) {
            event.setCancelled(true);
            return;
        }
        
        // 根据槽位类型分发处理
        if (rawSlot == ACTIVATION_SLOT) {
            handleActivationClick(event, player, gui);
        } else if (rawSlot == INTRO_BOOK_SLOT || rawSlot == STAR_INFO_BOOK_SLOT) {
            handleInfoBookClick(event);
        } else if (rawSlot == ANVIL_SWITCH_SLOT) {
            handleAnvilSwitchClick(event, player, gui);
        } else if (enchantGUIPlayers.contains(player.getUniqueId())) {
            handleEnchantGUIClick(event, player, gui, rawSlot);
        } else if (rawSlot == STAR_SLOT) {
            handleStarSlotClick(event, player, gui);
        } else {
            event.setCancelled(true);
        }
    }
    
    /**
     * 验证是否为有效的灾厄信标 GUI
     */
    private boolean isValidBeaconGUI(InventoryClickEvent event) {
        return event.getInventory().getHolder() instanceof BeaconInteractionListener;
    }
    
    /**
     * 处理背包槽位点击（Shift+点击更新预览）
     */
    private void handleBackpackClick(InventoryClickEvent event, Player player) {
        org.bukkit.event.inventory.ClickType clickType = event.getClick();
        boolean isShiftClick = (clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT || 
                                clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT);
        
        // 如果是 Shift+点击，且玩家正在灾厄强化 GUI 中，需要更新预览
        if (isShiftClick && enchantGUIPlayers.contains(player.getUniqueId())) {
            Location playerLocation = player.getLocation();
            Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                if (!player.isOnline()) return;
                
                org.bukkit.inventory.Inventory currentGui = player.getOpenInventory().getTopInventory();
                if (currentGui == null) return;
                
                updatePreview(currentGui, player);
            }, 2L);
        }
    }
    
    /**
     * 检查是否为可交互槽位
     */
    private boolean isInteractableSlot(int rawSlot) {
        return rawSlot == STAR_SLOT || rawSlot == ACTIVATION_SLOT || rawSlot == ANVIL_SWITCH_SLOT 
            || rawSlot == INPUT_TOOL_SLOT || rawSlot == INPUT_BOOK_SLOT || rawSlot == OUTPUT_SLOT;
    }
    
    /**
     * 处理激活按钮点击
     */
    private void handleActivationClick(InventoryClickEvent event, Player player, Inventory gui) {
        event.setCancelled(true);
        
        if (!event.getClick().isLeftClick()) {
            return;
        }
        
        // 检查是否有活跃的袭击
        if (plugin.getSpecialRaidListener().hasActiveRaid(player)) {
            sendActionBar(player, "§c⚠ 灾厄袭击正在进行中！");
            return;
        }
        
        // 使用打开GUI时保存的信标等级（避免跨线程调用getBeaconLevel）
        final int beaconLevel = playerBeaconLevels.getOrDefault(player.getUniqueId(), 0);
        
        // 调试日志（50% 概率）
        if (ThreadLocalRandom.current().nextDouble() < 0.5) {
            plugin.getLogger().info("§e[激活调试] 玩家 " + player.getName() + " 激活信标，保存的等级: " + beaconLevel);
        }
        
        ItemStack starItem = gui.getItem(STAR_SLOT);
        
        // 彩蛋级特殊处理
        if (beaconLevel == EASTER_EGG_LEVEL) {
            handleEasterEggActivation(player, gui, starItem, beaconLevel);
        } else {
            handleNormalActivation(player, gui, starItem, beaconLevel);
        }
    }
    
    /**
     * 处理彩蛋级信标激活
     */
    private void handleEasterEggActivation(Player player, Inventory gui, ItemStack starItem, int beaconLevel) {
        // ✅ 缓存 BeaconConfig 并从中读取彩蛋级激活材料
        ConfigManager.BeaconConfig eeBeaconConfig = configManager.getBeaconConfig();
        ConfigManager.ActivationConfig eeConfig = eeBeaconConfig != null ? eeBeaconConfig.getEasterEggActivationConfig() : null;
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            String materialName = (starItem != null) ? starItem.getType().name() : "null";
            int amount = (starItem != null) ? starItem.getAmount() : 0;
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 彩蛋激活 - 玩家: " + player.getName() + ", 材料: " + materialName + ", 数量: " + amount + ", 信标等级: " + beaconLevel);
        }
        if (eeConfig == null) {
            plugin.getLogger().warning("无效的彩蛋级激活材料配置");
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }
        
        String eeMaterialName = eeConfig.getMaterial();
        int eeAmount = eeConfig.getAmount();
        String eeDisplayName = eeConfig.getDisplayName();
        
        Material eeMaterial;
        try {
            eeMaterial = Material.valueOf(eeMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的彩蛋级激活材料配置：" + eeMaterialName);
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }
        
        // 检查是否为配置的激活材料
        if (starItem == null || starItem.getType() != eeMaterial) {
            sendActionBar(player, "§d⚠ 彩蛋信标需要 §d§l" + eeDisplayName + " §d才能激活！");
            return;
        }
        
        int bottleAmount = starItem.getAmount();
        
        // 只消耗配置的数量，多余的返还
        if (bottleAmount > eeAmount) {
            ItemStack dropBottles = new ItemStack(eeMaterial, bottleAmount - eeAmount);
            Location dropLocation = player.getLocation().add(0, 1.5, 0);
            
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                if (!player.isOnline()) return;
                player.getWorld().dropItemNaturally(dropLocation, dropBottles);
            });
            
            sendActionBar(player, "§e⚠ " + (bottleAmount - eeAmount) + " 个" + eeDisplayName + "已返还");
        } else if (bottleAmount == eeAmount) {
            sendActionBar(player, "§d✨ 已消耗 " + eeAmount + " x " + eeDisplayName + " 激活彩蛋信标！");
        } else {
            sendActionBar(player, "§c⚠ 需要 " + eeAmount + " x " + eeDisplayName + " 才能激活！");
            return;
        }
        
        // 消耗物品并给予灾厄效果
        gui.setItem(STAR_SLOT, null);
        activateBeaconAndGiveEffect(player, beaconLevel);
    }
    
    /**
     * 处理普通信标激活
     */
    private void handleNormalActivation(Player player, Inventory gui, ItemStack starItem, int beaconLevel) {
        // ✅ 缓存 BeaconConfig 并从中读取激活配置
        ConfigManager.BeaconConfig normalBeaconConfig = configManager.getBeaconConfig();
        ConfigManager.ActivationConfig activationConfig = normalBeaconConfig != null ? normalBeaconConfig.getActivationConfig() : null;
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            String materialName = (starItem != null) ? starItem.getType().name() : "null";
            int amount = (starItem != null) ? starItem.getAmount() : 0;
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 普通激活 - 玩家: " + player.getName() + ", 材料: " + materialName + ", 数量: " + amount + ", 信标等级: " + beaconLevel);
        }
        if (activationConfig == null) {
            plugin.getLogger().warning("无效的激活材料配置");
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }
        
        String activationMaterialName = activationConfig.getMaterial();
        int activationAmount = activationConfig.getAmount();
        String activationDisplayName = activationConfig.getDisplayName();
        
        Material activationMaterial;
        try {
            activationMaterial = Material.valueOf(activationMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的激活材料配置：" + activationMaterialName);
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }
        
        if (starItem == null || starItem.getType() != activationMaterial) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 普通激活失败 - 玩家: " + player.getName() + ", 缺少材料 " + activationDisplayName);
            }
            sendActionBar(player, "§c⚠ 没有" + activationDisplayName + "，无法激活！");
            return;
        }
        
        int starAmount = starItem.getAmount();
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 普通激活成功 - 玩家: " + player.getName() + ", 材料: " + activationDisplayName + ", 数量: " + starAmount + ", 需要: " + activationAmount);
        }
        
        if (starAmount > activationAmount) {
            ItemStack dropStars = new ItemStack(activationMaterial, starAmount - activationAmount);
            Location dropLocation = player.getLocation().add(0, 1.5, 0);
            
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                if (!player.isOnline()) return;
                player.getWorld().dropItemNaturally(dropLocation, dropStars);
            });
            
            sendActionBar(player, "§e⚠ " + (starAmount - activationAmount) + " 个" + activationDisplayName + "已掉落");
        } else if (starAmount == activationAmount) {
            sendActionBar(player, "§a✓ 已消耗 " + activationAmount + " x " + activationDisplayName + " 激活灾厄信标");
        } else {
            sendActionBar(player, "§c⚠ 需要 " + activationAmount + " x " + activationDisplayName + " 才能激活！");
            return;
        }
        
        // 消耗物品并给予灾厄效果
        gui.setItem(STAR_SLOT, null);
        activateBeaconAndGiveEffect(player, beaconLevel);
    }
    
    /**
     * 激活信标并给予灾厄效果（通用逻辑）
     */
    private void activateBeaconAndGiveEffect(Player player, int beaconLevel) {
        Location playerLoc = player.getLocation();
        
        Bukkit.getRegionScheduler().run(plugin, playerLoc, (task) -> {
            if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                plugin.getLogger().fine("激活信标等级：" + beaconLevel);
            }
            giveDoomEffect(player, beaconLevel);
        });
        
        // 关闭 GUI
        Bukkit.getRegionScheduler().runDelayed(plugin, playerLoc, (task) -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        }, 1L);
    }
    
    /**
     * 处理信息书点击（只取消事件）
     */
    private void handleInfoBookClick(InventoryClickEvent event) {
        event.setCancelled(true);
    }
    
    /**
     * 处理灾厄强化切换按钮点击
     */
    private void handleAnvilSwitchClick(InventoryClickEvent event, Player player, Inventory gui) {
        event.setCancelled(true);
        
        int beaconLevel = playerBeaconLevels.getOrDefault(player.getUniqueId(), 0);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 强化切换点击 - 玩家: " + player.getName() + ", 信标等级: " + beaconLevel);
        }
        
        // 检查信标等级是否满足要求（4级或彩蛋级）
        if (beaconLevel != 4 && beaconLevel != EASTER_EGG_LEVEL) {
            sendActionBar(player, "§c⚠ 需要 4 级信标或彩蛋级才能解锁灾厄强化！");
            return;
        }
        
        // 切换到灾厄强化 GUI
        Inventory enchantGUI = createEnchantGUI();
        player.openInventory(enchantGUI);
        enchantGUIPlayers.add(player.getUniqueId());
        startDisplayUpdate(player);
        sendActionBar(player, "§a✓ 已切换到灾厄强化界面");
    }
    
    /**
     * 处理星槽点击（放入/取出激活材料）
     */
    private void handleStarSlotClick(InventoryClickEvent event, Player player, Inventory gui) {
        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        
        // Use saved beacon level from GUI opening (avoid cross-thread getBeaconLevel)
        final int beaconLevel = playerBeaconLevels.getOrDefault(player.getUniqueId(), 0);
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            String cursorType = (cursor != null) ? cursor.getType().name() : "null";
            String currentType = (current != null) ? current.getType().name() : "null";
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 星槽点击 - 玩家: " + player.getName() + ", 光标物品: " + cursorType + ", 当前物品: " + currentType + ", 信标等级: " + beaconLevel);
        }
        
        // 彩蛋级特殊处理：只能放入配置的激活材料
        if (beaconLevel == EASTER_EGG_LEVEL) {
            // ✅ 缓存 BeaconConfig 并从中读取彩蛋级激活材料
            ConfigManager.BeaconConfig starSlotEeConfig = configManager.getBeaconConfig();
            ConfigManager.ActivationConfig eeConfig = starSlotEeConfig != null ? starSlotEeConfig.getEasterEggActivationConfig() : null;
            if (eeConfig == null) {
                sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
                return;
            }
            
            String eeMaterialName = eeConfig.getMaterial();
            int eeAmount = eeConfig.getAmount();
            String eeDisplayName = eeConfig.getDisplayName();
            
            Material eeMaterial;
            try {
                eeMaterial = Material.valueOf(eeMaterialName.toUpperCase());
            } catch (IllegalArgumentException e) {
                sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
                return;
            }
            
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (cursor.getType() != eeMaterial) {
                    event.setCancelled(true);
                    sendActionBar(player, "§d⚠ 彩蛋信标只能放入 §d§l" + eeDisplayName + "§d！");
                    return;
                }
                
                // 如果槽位已有激活材料，不允许堆叠（保持最多配置的数量）
                if (current != null && current.getType() == eeMaterial) {
                    event.setCancelled(true);
                    sendActionBar(player, "§e⚠ 已经有" + eeDisplayName + "了！");
                    return;
                }
                
                // 允许放入，但需要限制只能放配置的数量
                if (cursor.getAmount() > eeAmount) {
                    Location playerLocation = player.getLocation();
                    Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                        if (!player.isOnline()) return;
                        Inventory currentGui = player.getOpenInventory().getTopInventory();
                        if (currentGui == null) return;
                        
                        ItemStack slotItem = currentGui.getItem(STAR_SLOT);
                        if (slotItem != null && slotItem.getAmount() > eeAmount) {
                            int extra = slotItem.getAmount() - eeAmount;
                            slotItem.setAmount(eeAmount);
                            currentGui.setItem(STAR_SLOT, slotItem);
                            
                            ItemStack extraItems = new ItemStack(eeMaterial, extra);
                            java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(extraItems);
                            if (!remaining.isEmpty()) {
                                for (ItemStack drop : remaining.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), drop);
                                }
                            }
                            sendActionBar(player, "§e⚠ 只能放入" + eeAmount + "个" + eeDisplayName + "，多余已返还");
                        }
                    }, 1L);
                }
                // 允许放入，不取消事件
                return;
            }
            // 鼠标为空，允许拿起
            return;
        }
        
        // Normal beacon: use nether star
        // ✅ 缓存 BeaconConfig 并从中读取激活配置
        ConfigManager.BeaconConfig starSlotConfig = configManager.getBeaconConfig();
        ConfigManager.ActivationConfig activationConfig = starSlotConfig != null ? starSlotConfig.getActivationConfig() : null;
        if (activationConfig == null) {
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }
        
        String activationMaterialName = activationConfig.getMaterial();
        int activationAmount = activationConfig.getAmount();
        String activationDisplayName = activationConfig.getDisplayName();
        
        Material activationMaterial;
        try {
            activationMaterial = Material.valueOf(activationMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }
        
        // 允许原版容器交互：不取消事件，只在必要时验证
        if (cursor != null && cursor.getType() != Material.AIR) {
            // 鼠标上有物品，尝试放入
            if (cursor.getType() != activationMaterial) {
                event.setCancelled(true);
                sendActionBar(player, "§c⚠ 只能放入" + activationDisplayName + "！");
                return;
            }
            
            // 如果槽位已有激活材料，不允许堆叠（保持最多配置的数量）
            if (current != null && current.getType() == activationMaterial) {
                event.setCancelled(true);
                sendActionBar(player, "§e⚠ 已经有" + activationDisplayName + "了！");
                return;
            }
            
            // 允许放入，不取消事件，让 Bukkit 处理
            // 但需要限制只能放配置的数量
            if (cursor.getAmount() > activationAmount) {
                //  使用 RegionScheduler 切换到玩家所在区域线程
                Location playerLocation = player.getLocation();
                Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                    if (!player.isOnline()) return;
                    Inventory currentGui = player.getOpenInventory().getTopInventory();
                    if (currentGui == null) return;
                    
                    ItemStack slotItem = currentGui.getItem(STAR_SLOT);
                    if (slotItem != null && slotItem.getAmount() > activationAmount) {
                        // 如果超过配置数量，只保留配置数量，多余的返回给玩家
                        int extra = slotItem.getAmount() - activationAmount;
                        slotItem.setAmount(activationAmount);
                        currentGui.setItem(STAR_SLOT, slotItem);
                        
                        ItemStack extraItems = new ItemStack(activationMaterial, extra);
                        java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(extraItems);
                        if (!remaining.isEmpty()) {
                            for (ItemStack drop : remaining.values()) {
                                player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), drop);
                            }
                        }
                        sendActionBar(player, "§e⚠ 只能放入" + activationAmount + "个" + activationDisplayName + "，多余已返还");
                    }
                }, 1L);
            }
            // 不取消事件，允许放入
            return;
        }
        
        // Mouse empty, click slot to pick up item (vanilla behavior)
        // Do not cancel event, let Bukkit handle pickup logic
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BeaconInteractionListener)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        Inventory gui = event.getInventory();
        UUID playerUuid = player.getUniqueId();
        
        // 处理灾厄强化 GUI 的物品返还
        if (enchantGUIPlayers.contains(playerUuid)) {
            enchantGUIPlayers.remove(playerUuid);
            stopDisplayUpdate(playerUuid);
            
            ItemStack toolItem = gui.getItem(INPUT_TOOL_SLOT);
            ItemStack bookItem = gui.getItem(INPUT_BOOK_SLOT);
            
            if (toolItem != null && !isPlaceholderItem(toolItem)) {
                Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                    if (!player.isOnline()) return;
                    Location dropLocation = player.getLocation().add(0, 1, 0);
                    player.getWorld().dropItemNaturally(dropLocation, toolItem);
                });
            }
            
            if (bookItem != null && !isPlaceholderItem(bookItem)) {
                Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                    if (!player.isOnline()) return;
                    Location dropLocation = player.getLocation().add(0, 1, 0);
                    player.getWorld().dropItemNaturally(dropLocation, bookItem);
                });
            }
            
            if (toolItem != null && !isPlaceholderItem(toolItem) || 
                bookItem != null && !isPlaceholderItem(bookItem)) {
                sendActionBar(player, "§e⚠ GUI 已关闭，物品已掉落");
            }
            return;
        }
        
        // 处理灾厄信标 GUI - 清理所有相关状态
        // 清理自动切换任务
        stopDisplayUpdate(playerUuid);
        
        // 清理玩家映射中的信标等级
        playerBeaconLevels.remove(playerUuid);
        
        // 清理玩家的显示索引（额外保险，stopDisplayUpdate 已经清理过）
        playerDisplayIndexes.remove(playerUuid);
        
        // 检查消耗槽位中的物品
        ItemStack starItem = gui.getItem(STAR_SLOT);
        if (starItem == null || starItem.getType() == Material.AIR) {
            return;
        }
        
        // 无论什么物品都返还
        final ItemStack itemToDrop = starItem.clone();
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;
            Location dropLocation = player.getLocation().add(0, 1, 0);
            player.getWorld().dropItemNaturally(dropLocation, itemToDrop);
        });
        sendActionBar(player, "§e⚠ GUI 已关闭，物品已掉落");
    }
    
    /**
     * 处理灾厄强化 GUI 的点击事件
     */
    private void handleEnchantGUIClick(InventoryClickEvent event, Player player, Inventory gui, int rawSlot) {
        if (rawSlot == INPUT_TOOL_SLOT || rawSlot == INPUT_BOOK_SLOT) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                String slotName = (rawSlot == INPUT_TOOL_SLOT) ? "工具槽(29)" : "种子槽(31)";
                ItemStack cursor = event.getCursor();
                ItemStack current = event.getCurrentItem();
                String cursorType = (cursor != null) ? cursor.getType().name() : "null";
                String currentType = (current != null) ? current.getType().name() : "null";
                plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 强化槽点击 - 玩家: " + player.getName() + ", 槽位: " + slotName + ", 光标: " + cursorType + ", 当前: " + currentType);
            }
            
            org.bukkit.event.inventory.ClickType clickType = event.getClick();
            
            // 处理 Shift+点击：不取消事件，让 Bukkit 处理
            if (clickType == org.bukkit.event.inventory.ClickType.SHIFT_LEFT || 
                clickType == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT) {
                Location playerLocation = player.getLocation();
                Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                    if (!player.isOnline()) return;
                    org.bukkit.inventory.Inventory currentGui = player.getOpenInventory().getTopInventory();
                    if (currentGui != null) updatePreview(currentGui, player);
                }, 2L);
                return;
            }
            
            // 普通点击：允许原版容器交互（拿起-放置）
            ItemStack cursor = event.getCursor();
            ItemStack current = event.getCurrentItem();
            
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (rawSlot == INPUT_TOOL_SLOT) {
                    if (!isEnchantableItem(cursor)) {
                        event.setCancelled(true);
                        sendActionBar(player, "§c⚠ 只能放入工具、武器或盔甲！");
                        return;
                    }
                } else if (rawSlot == INPUT_BOOK_SLOT) {
                    // 31槽位现在只允许放入灾厄之种
                    String name = cursor.hasItemMeta() && cursor.getItemMeta().hasDisplayName() ? cursor.getItemMeta().getDisplayName() : "";
                    if (!name.contains("灾厄之种I") && !name.contains("灾厄之种II")) {
                        event.setCancelled(true);
                        sendActionBar(player, "§c⚠ 只能放入灾厄之种！");
                        return;
                    }
                }
                
                Location playerLocation = player.getLocation();
                Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                    if (!player.isOnline()) return;
                    org.bukkit.inventory.Inventory currentGui = player.getOpenInventory().getTopInventory();
                    if (currentGui != null) updatePreview(currentGui, player);
                }, 1L);
                return;
            }
            
            if (current != null && !isPlaceholderItem(current)) {
                Location playerLocation = player.getLocation();
                Bukkit.getRegionScheduler().runDelayed(plugin, playerLocation, (task) -> {
                    if (!player.isOnline()) return;
                    org.bukkit.inventory.Inventory currentGui = player.getOpenInventory().getTopInventory();
                    if (currentGui != null) updatePreview(currentGui, player);
                }, 1L);
            }
            return;
        }
        
        if (rawSlot == OUTPUT_SLOT) {
            if (plugin.getConfigManager().isDebugEnabled()) {
                ItemStack outputItem = gui.getItem(OUTPUT_SLOT);
                String outputType = (outputItem != null) ? outputItem.getType().name() : "null";
                plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 输出槽点击 - 玩家: " + player.getName() + ", 当前输出: " + outputType);
            }
            
            event.setCancelled(true);
            
            ItemStack outputItem = gui.getItem(OUTPUT_SLOT);
            
            // 1. 如果输出槽有物品，且不是屏障方块或占位符，则允许玩家取出
            if (outputItem != null && outputItem.getType() != Material.BARRIER && !isPlaceholderItem(outputItem)) {
                // ✅ Folia 兼容：在玩家所在区域线程中操作背包和掉落物品
                Location playerLoc = player.getLocation();
                Bukkit.getRegionScheduler().run(plugin, playerLoc, (task) -> {
                    if (!player.isOnline()) return;
                    
                    // 尝试将物品放入玩家背包
                    java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(outputItem);
                    
                    // 如果背包满了，把剩下的掉落在地上
                    if (!remaining.isEmpty()) {
                        for (ItemStack drop : remaining.values()) {
                            player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), drop);
                        }
                        sendActionBar(player, "§e⚠ 背包已满，部分物品已掉落");
                    } else {
                        sendActionBar(player, "§a✓ 已取出强化后的物品！");
                    }
                });
                
                // 清空输出槽，准备下一次强化
                gui.setItem(OUTPUT_SLOT, null);
                return;
            }
            
            // 2. 如果输出槽是空的、或者是屏障/占位符，则执行强化逻辑
            ItemStack toolItem = gui.getItem(INPUT_TOOL_SLOT);
            ItemStack seedItem = gui.getItem(INPUT_BOOK_SLOT); // 31槽现在是种子
            
            if (toolItem == null || isPlaceholderItem(toolItem) || seedItem == null || isPlaceholderItem(seedItem)) {
                sendActionBar(player, "§c⚠ 请先放入物品和灾厄之种！");
                return;
            }
            
            // 检查是否已达强化上限（3次）
            if (!seedManager.canEnhance(toolItem)) {
                ItemStack barrier = new ItemStack(Material.BARRIER);
                ItemMeta meta = barrier.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("已达到强化上限")
                        .color(TextColor.color(0xFF5555))
                        .decorate(TextDecoration.BOLD));
                    meta.lore(Arrays.asList(
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§7该物品已消耗 3 个灾厄之种"),
                        net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize("§7无法继续强化")
                    ));
                    barrier.setItemMeta(meta);
                }
                gui.setItem(OUTPUT_SLOT, barrier);
                sendActionBar(player, "§c⚠ 该物品已强化至上限！");
                return;
            }
            
            // 执行强化逻辑
            ItemStack result = seedManager.performEnhancement(toolItem.clone(), seedItem);
            if (result == null) {
                sendActionBar(player, "§c⚠ 强化失败或无可用属性！");
                return;
            }
            
            // ✅ 消耗 29 槽的物品和 31 槽的 1 个灾厄之种
            gui.setItem(INPUT_TOOL_SLOT, null);
            int amount = seedItem.getAmount();
            if (amount > 1) {
                seedItem.setAmount(amount - 1);
            } else {
                gui.setItem(INPUT_BOOK_SLOT, null);
            }
            
            // 将强化后的物品放入输出槽
            // ✅ 添加灾厄强化标记，防止铁砧修改
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "disaster_enhanced");
            ItemMeta resultMeta = result.getItemMeta();
            if (resultMeta != null) {
                resultMeta.getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                result.setItemMeta(resultMeta);
            }
            
            gui.setItem(OUTPUT_SLOT, result);
            sendActionBar(player, "§a✓ 强化成功！请点击取出。");
            return;
        }
        
        event.setCancelled(true);
    }
    
    /**
     * 检查物品是否可以附魔
     */
    private boolean isEnchantableItem(ItemStack item) {
        Material type = item.getType();
        return type.name().endsWith("_HELMET") ||
               type.name().endsWith("_CHESTPLATE") ||
               type.name().endsWith("_LEGGINGS") ||
               type.name().endsWith("_BOOTS") ||
               type.name().endsWith("_SWORD") ||
               type.name().endsWith("_PICKAXE") ||
               type.name().endsWith("_AXE") ||
               type.name().endsWith("_SHOVEL") ||
               type.name().endsWith("_HOE") ||
               type == Material.BOW ||
               type == Material.CROSSBOW ||
               type == Material.TRIDENT ||
               type == Material.FISHING_ROD ||
               type == Material.SHIELD ||
               type == Material.ELYTRA;
    }
    
    /**
     * 检查是否为占位符物品
     */
    private boolean isPlaceholderItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String name = meta.getDisplayName();
        return name != null && (name.contains("放置") || name.contains("预览"));
    }
    
    /**
     * 更新预览
     */
    private void updatePreview(Inventory gui, Player player) {
        ItemStack toolItem = gui.getItem(INPUT_TOOL_SLOT);
        ItemStack bookItem = gui.getItem(INPUT_BOOK_SLOT);
        
        if (toolItem == null || isPlaceholderItem(toolItem) || 
            bookItem == null || isPlaceholderItem(bookItem)) {
            ItemStack placeholder = new ItemStack(Material.ANVIL);
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a§l附魔预览");
                meta.setLore(Arrays.asList(
                    "§7━━━━━━━━━━━━━━━━",
                    "§f放置物品和附魔书后",
                    "§f此处会显示预览结果",
                    "",
                    "§e点击取出附魔后的物品",
                    "§c消耗：物品 + 附魔书",
                    "§7━━━━━━━━━━━━━━━━"
                ));
                placeholder.setItemMeta(meta);
            }
            gui.setItem(OUTPUT_SLOT, placeholder);
            return;
        }
        
        ItemStack preview = toolItem.clone();
        ItemMeta toolMeta = preview.getItemMeta();
        ItemMeta bookMeta = bookItem.getItemMeta();
        
        if (toolMeta == null || !(bookMeta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta)) {
            // 元数据无效，显示默认预览
            ItemStack placeholder = new ItemStack(Material.ANVIL);
            ItemMeta meta = placeholder.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a§l附魔预览");
                meta.setLore(Arrays.asList(
                    "§7━━━━━━━━━━━━━━━━",
                    "§f放置物品和附魔书后",
                    "§f此处会显示预览结果",
                    "",
                    "§e点击取出附魔后的物品",
                    "§c消耗：物品 + 附魔书",
                    "§7━━━━━━━━━━━━━━━━"
                ));
                placeholder.setItemMeta(meta);
            }
            gui.setItem(OUTPUT_SLOT, placeholder);
            return;
        }
        
        org.bukkit.inventory.meta.EnchantmentStorageMeta bookEnchantMeta = 
            (org.bukkit.inventory.meta.EnchantmentStorageMeta) bookMeta;
        
        Map<Enchantment, Integer> existingEnchants = toolMeta.getEnchants();
        if (existingEnchants == null) {
            existingEnchants = new java.util.HashMap<>();
        }
        
        for (var entry : bookEnchantMeta.getStoredEnchants().entrySet()) {
            EnchantmentRuleManager ruleManager = EnchantmentRuleManager.getInstance();
            if (ruleManager != null && ruleManager.canApplyEnchantment(toolItem, entry.getKey())) {
                if (!ruleManager.hasConflict(toolItem, entry.getKey(), existingEnchants)) {
                    toolMeta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            }
        }
        
        preview.setItemMeta(toolMeta);
        gui.setItem(OUTPUT_SLOT, preview);
    }
    
    /**
     * 启动自动切换显示
     */
    private void startDisplayUpdate(Player player) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 启动显示轮播任务 - 玩家: " + player.getName());
        }
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            if (!player.isOnline() || !enchantGUIPlayers.contains(player.getUniqueId())) {
                stopDisplayUpdate(player.getUniqueId());
                return;
            }
            
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task2) -> {
                if (!player.isOnline() || !enchantGUIPlayers.contains(player.getUniqueId())) return;
                
                org.bukkit.inventory.Inventory gui = player.getOpenInventory().getTopInventory();
                if (gui == null) return;
                
                // 获取并更新该玩家的显示索引
                int currentIndex = playerDisplayIndexes.getOrDefault(player.getUniqueId(), 0);
                currentIndex = (currentIndex + 1) % DISPLAY_ITEMS.length;
                playerDisplayIndexes.put(player.getUniqueId(), currentIndex);
                Material currentItem = DISPLAY_ITEMS[currentIndex];
                
                gui.setItem(DISPLAY_TOOL_SLOT, createDisplayItem(currentItem, "§b§l示例物品"));
                gui.setItem(DISPLAY_PREVIEW_SLOT, createDisplayItem(Material.ANVIL, "§a§l附魔预览"));
            });
        }, 1L, 20L); // 初始延迟改为 1 tick
        
        displayUpdateTasks.put(player.getUniqueId(), task);
    }
    
    /**
     * 停止自动切换显示
     */
    private void stopDisplayUpdate(java.util.UUID playerId) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 停止显示轮播任务 - 玩家UUID: " + playerId);
        }
        io.papermc.paper.threadedregions.scheduler.ScheduledTask task = displayUpdateTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        // 清理玩家的显示索引
        playerDisplayIndexes.remove(playerId);
    }
    
    /**
     * ✅ 监听铁砧事件，阻止灾厄强化物品被修改
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();
        
        if (result != null && result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                // 检查是否有灾厄强化标记
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "disaster_enhanced");
                byte isEnhanced = meta.getPersistentDataContainer().getOrDefault(
                    key,
                    org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) 0
                );
                
                if (isEnhanced == 1) {
                    // 阻止铁砧操作
                    event.setResult(new ItemStack(Material.AIR));
                    
                    // 发送提示消息给玩家
                    for (org.bukkit.entity.HumanEntity viewer : event.getViewers()) {
                        if (viewer instanceof Player) {
                            Player player = (Player) viewer;
                            player.sendMessage("§c❌ 该物品已被灾厄强化，无法通过铁砧修改！");
                        }
                    }
                }
            }
        }
    }
}