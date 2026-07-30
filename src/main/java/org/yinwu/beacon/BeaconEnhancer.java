package org.yinwu.beacon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灾厄强化 GUI 逻辑
 * 管理灾厄强化的界面展示、种子应用和星级计算
 */
public class BeaconEnhancer {

    private final YinwuRaidPlugin plugin;
    private final ConfigManager configManager;
    private final DisasterSeedManager seedManager;

    // 显示槽位
    public static final int DISPLAY_TOOL_SLOT = 20;
    public static final int DISPLAY_BOOK_SLOT = 22;
    public static final int DISPLAY_PREVIEW_SLOT = 24;

    // 操作槽位
    public static final int INPUT_TOOL_SLOT = 29;
    public static final int INPUT_BOOK_SLOT = 31;
    public static final int OUTPUT_SLOT = 33;

    private static final String ENCHANT_GUI_TITLE = "§6§l灾厄强化";

    private static final Material[] DISPLAY_ITEMS = {
        Material.NETHERITE_PICKAXE,
        Material.NETHERITE_SWORD,
        Material.NETHERITE_CHESTPLATE,
        Material.NETHERITE_HELMET,
        Material.BOW,
        Material.TRIDENT
    };

    // 玩家状态跟踪
    private final Set<UUID> enchantGUIPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> playerDisplayIndexes = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> displayUpdateTasks = new ConcurrentHashMap<>();

    /**
     * 强化 GUI 的 InventoryHolder，用于事件路由识别
     */
    public static class EnchantGUIHolder implements InventoryHolder {
        @Override
        public @Nullable Inventory getInventory() {
            return null;
        }
    }

    public BeaconEnhancer(YinwuRaidPlugin plugin, ConfigManager configManager, DisasterSeedManager seedManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.seedManager = seedManager;
    }

    // ==================== GUI 开启 ====================

    /**
     * 打开灾厄强化 GUI
     */
    public void openEnhancementGUI(Player player) {
        Inventory gui = createEnchantGUI();
        player.openInventory(gui);
        enchantGUIPlayers.add(player.getUniqueId());
        startDisplayUpdate(player);
        sendActionBar(player, "§a✓ 已切换到灾厄强化界面");
    }

    /**
     * 打开彩蛋级灾厄强化 GUI
     */
    public void openEasterEggEnhancementGUI(Player player) {
        openEnhancementGUI(player);
    }

    // ==================== 输出槽处理 ====================

    /**
     * 处理输出槽点击：取出物品或执行强化
     */
    public void handleOutputSlotClick(Player player, Inventory gui) {
        ItemStack outputItem = gui.getItem(OUTPUT_SLOT);

        // 1. 输出槽有实际物品 → 取出
        if (hasRealOutput(outputItem)) {
            retrieveOutput(player, gui, outputItem);
            return;
        }

        // 2. 执行强化
        performEnhancement(player, gui);
    }

    /**
     * 安排预览更新（输入槽变更后延迟执行）
     */
    public void schedulePreviewUpdate(Player player) {
        if (!player.isOnline()) return;
        Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;
            Inventory gui = player.getOpenInventory().getTopInventory();
            if (gui != null && isInEnchantGUI(player)) {
                updatePreview(gui, player);
            }
        }, 1L);
    }

    // ==================== GUI 关闭处理 ====================

    /**
     * 处理强化 GUI 关闭：返还物品并清理状态
     */
    public void handleGuiClose(Player player, Inventory gui) {
        UUID playerId = player.getUniqueId();
        enchantGUIPlayers.remove(playerId);
        stopDisplayUpdate(playerId);

        ItemStack toolItem = gui.getItem(INPUT_TOOL_SLOT);
        ItemStack bookItem = gui.getItem(INPUT_BOOK_SLOT);

        boolean hasItems = false;
        if (toolItem != null && !isPlaceholderItem(toolItem)) {
            dropItem(player, toolItem);
            hasItems = true;
        }
        if (bookItem != null && !isPlaceholderItem(bookItem)) {
            dropItem(player, bookItem);
            hasItems = true;
        }
        if (hasItems) {
            sendActionBar(player, "§e⚠ GUI 已关闭，物品已掉落");
        }
    }

    // ==================== 状态查询 ====================

    public boolean isInEnchantGUI(UUID playerId) {
        return enchantGUIPlayers.contains(playerId);
    }

    public boolean isInEnchantGUI(Player player) {
        return enchantGUIPlayers.contains(player.getUniqueId());
    }

    // ==================== 公共辅助方法 ====================

    /**
     * 检查物品是否可附魔
     */
    public boolean isEnchantableItem(ItemStack item) {
        if (item == null) return false;
        Material type = item.getType();
        return type.name().endsWith("_HELMET")
            || type.name().endsWith("_CHESTPLATE")
            || type.name().endsWith("_LEGGINGS")
            || type.name().endsWith("_BOOTS")
            || type.name().endsWith("_SWORD")
            || type.name().endsWith("_PICKAXE")
            || type.name().endsWith("_AXE")
            || type.name().endsWith("_SHOVEL")
            || type.name().endsWith("_HOE")
            || type == Material.BOW
            || type == Material.CROSSBOW
            || type == Material.TRIDENT
            || type == Material.FISHING_ROD
            || type == Material.SHIELD
            || type == Material.ELYTRA;
    }

    /**
     * 检查是否为灾厄之种
     */
    public boolean isValidSeed(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
            ? item.getItemMeta().getDisplayName() : "";
        return name.contains("灾厄之种I") || name.contains("灾厄之种II");
    }

    /**
     * 检查是否为占位符物品
     */
    public boolean isPlaceholderItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return true;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String name = meta.getDisplayName();
        return name != null && (name.contains("放置") || name.contains("预览"));
    }

    /**
     * 发送 ActionBar 消息（Folia 兼容）
     */
    public void sendActionBar(Player player, String message) {
        if (!player.isOnline()) return;
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;
            try {
                player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(message));
                Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), (clearTask) -> {
                    if (player.isOnline()) {
                        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(""));
                    }
                }, 60L);
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 发送 ActionBar 失败：" + e.getMessage());
            }
        });
    }

    // ==================== 内部：GUI 创建 ====================

    /**
     * 创建强化 GUI（使用独立的 InventoryHolder）
     */
    private Inventory createEnchantGUI() {
        Inventory gui = Bukkit.createInventory(new EnchantGUIHolder(), 54, ENCHANT_GUI_TITLE);

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

    // ==================== 内部：显示管理 ====================

    /**
     * 启动自动切换显示轮播
     */
    private void startDisplayUpdate(Player player) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconEnhancer] 启动显示轮播任务 - 玩家: " + player.getName());
        }
        ScheduledTask task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            if (!player.isOnline() || !enchantGUIPlayers.contains(player.getUniqueId())) {
                stopDisplayUpdate(player.getUniqueId());
                return;
            }
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task2) -> {
                if (!player.isOnline() || !enchantGUIPlayers.contains(player.getUniqueId())) return;
                Inventory gui = player.getOpenInventory().getTopInventory();
                if (gui == null) return;

                int currentIndex = playerDisplayIndexes.getOrDefault(player.getUniqueId(), 0);
                currentIndex = (currentIndex + 1) % DISPLAY_ITEMS.length;
                playerDisplayIndexes.put(player.getUniqueId(), currentIndex);
                Material currentItem = DISPLAY_ITEMS[currentIndex];

                gui.setItem(DISPLAY_TOOL_SLOT, createDisplayItem(currentItem, "§b§l示例物品"));
                gui.setItem(DISPLAY_PREVIEW_SLOT, createDisplayItem(Material.ANVIL, "§a§l附魔预览"));
            });
        }, 1L, 20L);

        displayUpdateTasks.put(player.getUniqueId(), task);
    }

    /**
     * 停止自动切换显示轮播
     */
    private void stopDisplayUpdate(UUID playerId) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconEnhancer] 停止显示轮播任务 - 玩家UUID: " + playerId);
        }
        ScheduledTask task = displayUpdateTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        playerDisplayIndexes.remove(playerId);
    }

    /**
     * 更新强化预览槽位
     */
    private void updatePreview(Inventory gui, Player player) {
        ItemStack toolItem = gui.getItem(INPUT_TOOL_SLOT);
        ItemStack bookItem = gui.getItem(INPUT_BOOK_SLOT);

        if (toolItem == null || isPlaceholderItem(toolItem)
            || bookItem == null || isPlaceholderItem(bookItem)) {
            gui.setItem(OUTPUT_SLOT, createDefaultPreview());
            return;
        }

        ItemStack preview = toolItem.clone();
        ItemMeta toolMeta = preview.getItemMeta();
        ItemMeta bookMeta = bookItem.getItemMeta();

        if (toolMeta == null || !(bookMeta instanceof EnchantmentStorageMeta)) {
            gui.setItem(OUTPUT_SLOT, createDefaultPreview());
            return;
        }

        Map<Enchantment, Integer> existingEnchants = toolMeta.getEnchants();
        if (existingEnchants == null) {
            existingEnchants = new java.util.HashMap<>();
        }

        EnchantmentStorageMeta bookEnchantMeta = (EnchantmentStorageMeta) bookMeta;
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

    // ==================== 内部：强化逻辑 ====================

    /**
     * 执行强化
     */
    private void performEnhancement(Player player, Inventory gui) {
        ItemStack toolItem = gui.getItem(INPUT_TOOL_SLOT);
        ItemStack seedItem = gui.getItem(INPUT_BOOK_SLOT);

        if (toolItem == null || isPlaceholderItem(toolItem)
            || seedItem == null || isPlaceholderItem(seedItem)) {
            sendActionBar(player, "§c⚠ 请先放入物品和灾厄之种！");
            return;
        }

        // 检查强化上限
        if (!seedManager.canEnhance(toolItem)) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta meta = barrier.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("已达到强化上限")
                    .color(TextColor.color(0xFF5555))
                    .decorate(TextDecoration.BOLD));
                meta.lore(Arrays.asList(
                    LegacyComponentSerializer.legacySection().deserialize("§7该物品已消耗 3 个灾厄之种"),
                    LegacyComponentSerializer.legacySection().deserialize("§7无法继续强化")
                ));
                barrier.setItemMeta(meta);
            }
            gui.setItem(OUTPUT_SLOT, barrier);
            sendActionBar(player, "§c⚠ 该物品已强化至上限！");
            return;
        }

        // 执行强化
        ItemStack result = seedManager.performEnhancement(toolItem.clone(), seedItem);
        if (result == null) {
            sendActionBar(player, "§c⚠ 强化失败或无可用属性！");
            return;
        }

        // 消耗物品
        gui.setItem(INPUT_TOOL_SLOT, null);
        int amount = seedItem.getAmount();
        if (amount > 1) {
            seedItem.setAmount(amount - 1);
        } else {
            gui.setItem(INPUT_BOOK_SLOT, null);
        }

        // 添加灾厄强化 PDC 标记，防止铁砧修改
        ItemStack resultWithMarker = addEnhancementMarker(result);

        gui.setItem(OUTPUT_SLOT, resultWithMarker);
        sendActionBar(player, "§a✓ 强化成功！请点击取出。");
    }

    /**
     * 取走输出槽的物品
     */
    private void retrieveOutput(Player player, Inventory gui, ItemStack outputItem) {
        Location playerLoc = player.getLocation();
        Bukkit.getRegionScheduler().run(plugin, playerLoc, (task) -> {
            if (!player.isOnline()) return;
            java.util.HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(outputItem);
            if (!remaining.isEmpty()) {
                for (ItemStack drop : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), drop);
                }
                sendActionBar(player, "§e⚠ 背包已满，部分物品已掉落");
            } else {
                sendActionBar(player, "§a✓ 已取出强化后的物品！");
            }
        });
        gui.setItem(OUTPUT_SLOT, null);
    }

    // ==================== 内部：辅助方法 ====================

    /**
     * 创建显示物品
     */
    private ItemStack createDisplayItem(Material material, String displayName) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LegacyComponentSerializer.legacySection().deserialize(displayName));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 检查输出槽是否有实际物品可取
     */
    private boolean hasRealOutput(ItemStack item) {
        return item != null && item.getType() != Material.BARRIER && !isPlaceholderItem(item);
    }

    /**
     * 创建默认强化预览占位符
     */
    private ItemStack createDefaultPreview() {
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
        return placeholder;
    }

    /**
     * 添加灾厄强化 PDC 标记
     */
    private ItemStack addEnhancementMarker(ItemStack item) {
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "disaster_enhanced");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key,
                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * 掉落物品到玩家位置
     */
    private void dropItem(Player player, ItemStack item) {
        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;
            player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), item);
        });
    }
}
