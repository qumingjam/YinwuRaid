package org.yinwu.gui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.beacon.BeaconInteractionListener;
import org.yinwu.beacon.DisasterSeedManager;
import org.yinwu.config.ConfigManager;
import org.yinwu.config.BeaconConfig;
import org.yinwu.config.ActivationConfig;
import org.yinwu.config.LayerConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BeaconGUI {

    /** 激活按钮槽位（槽 44 材料槽正下方） */
    public static final int ACTIVATE_SLOT = 53;
    /** 激活材料槽位（下界之星 / 不详之瓶） */
    public static final int MATERIAL_SLOT = 44;
    /** 灾厄强化入口按钮槽位（铁块层正下方） */
    public static final int ENHANCE_BUTTON_SLOT = 49;

    private final YinwuRaidPlugin plugin;
    private final BeaconInteractionListener listener;
    private final ConfigManager configManager;
    private final DisasterSeedManager seedManager;

    public BeaconGUI(YinwuRaidPlugin plugin, BeaconInteractionListener listener, ConfigManager configManager, DisasterSeedManager seedManager) {
        this.plugin = plugin;
        this.listener = listener;
        this.configManager = configManager;
        this.seedManager = seedManager;
    }

    /**
     * 打开灾厄信标 GUI。
     * @param detectedLevel 右键时检测到的信标等级（决定各层 ✓/✗ 显示）
     */
    public void openBeaconGUI(Player player, int detectedLevel) {
        Inventory gui = plugin.getServer().createInventory(null, 54, "§4§l灾厄信标");
        BeaconConfig bc = configManager.getBeaconConfig();
        Map<Integer, LayerConfig> layers = bc != null ? bc.getLayers() : null;

        // 第1行：红色装饰条
        for (int i = 0; i < 9; i++) gui.setItem(i, pane(Material.RED_STAINED_GLASS_PANE));

        // 彩蛋（满层绿宝石）时四层全绿宝石；否则按检测等级显示已构建层
        boolean easter = detectedLevel >= 6;
        setLayerRow(gui, 4, easter ? Material.EMERALD_BLOCK : layerMaterial(layers, 4, Material.NETHERITE_BLOCK), easter || detectedLevel >= 4);
        setLayerRow(gui, 3, easter ? Material.EMERALD_BLOCK : layerMaterial(layers, 3, Material.DIAMOND_BLOCK), easter || detectedLevel >= 3);
        setLayerRow(gui, 2, easter ? Material.EMERALD_BLOCK : layerMaterial(layers, 2, Material.GOLD_BLOCK), easter || detectedLevel >= 2);
        setLayerRow(gui, 1, easter ? Material.EMERALD_BLOCK : layerMaterial(layers, 1, Material.IRON_BLOCK), easter || detectedLevel >= 1);

        setEnhanceButton(gui);
        setActivationButton(gui, bc);
        // 槽 44 材料槽留空供放入（fillFrame 跳过，保证可放入/可取出）
        fillFrame(gui);
        player.openInventory(gui);
    }

    /** 激活按钮（槽 53，材料槽正下方） */
    private void setActivationButton(Inventory inv, BeaconConfig bc) {
        ItemStack btn = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§l激活灾厄信标");
            List<String> lore = new ArrayList<>();
            lore.add("§7上方填入 §f下界之星 §7或 §d不详之瓶 §7×64");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        inv.setItem(ACTIVATE_SLOT, btn);
    }

    public boolean handleClick(Player player, int slot, Inventory inv) {
        BeaconConfig bc = configManager.getBeaconConfig();
        if (bc == null) return false;

        // 灾厄强化入口
        if (slot == ENHANCE_BUTTON_SLOT) {
            listener.openEnhancer(player);
            return true;
        }

        // 激活按钮：彩蛋由结构等级决定，监听器按等级判断
        if (slot == ACTIVATE_SLOT) {
            listener.tryActivateBeacon(player, false);
            return true;
        }
        return false;
    }

    private void setEnhanceButton(Inventory inv) {
        ItemStack btn = new ItemStack(Material.ANVIL);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l灾厄强化");
            List<String> lore = new ArrayList<>();
            lore.add("§7将物品 + 灾厄之种放入强化台");
            lore.add("§7可强化附魔等级");
            meta.setLore(lore);
            btn.setItemMeta(meta);
        }
        inv.setItem(ENHANCE_BUTTON_SLOT, btn);
    }

    /** 金字塔层显示：level4→第2行9格、level3→第3行7格、level2→第4行5格、level1→第5行3格 */
    private void setLayerRow(Inventory inv, int level, Material material, boolean built) {
        if (!built) return;  // 未构建：留给 fillFrame 放玻璃占位
        int start, count;
        switch (level) {
            case 4 -> { start = 9; count = 9; }
            case 3 -> { start = 19; count = 7; }
            case 2 -> { start = 29; count = 5; }
            case 1 -> { start = 39; count = 3; }
            default -> { return; }
        }
        ItemStack block = new ItemStack(material, count);
        ItemMeta meta = block.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6第 " + level + " 层");
            List<String> lore = new ArrayList<>();
            lore.add("§7方块: " + material.name());
            lore.add("§7尺寸: " + count + "x" + count);
            lore.add("§7状态: §a✓ 已构建");
            meta.setLore(lore);
            block.setItemMeta(meta);
        }
        for (int i = 0; i < count; i++) inv.setItem(start + i, block.clone());
    }

    private Material layerMaterial(Map<Integer, LayerConfig> layers, int level, Material defaultMat) {
        if (layers != null) {
            LayerConfig lc = layers.get(level);
            if (lc != null) {
                try { return Material.valueOf(lc.getMaterial()); } catch (Exception ignored) {}
            }
        }
        return defaultMat;
    }

    private void fillFrame(Inventory inv) {
        ItemStack red = pane(Material.RED_STAINED_GLASS_PANE);
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack yellow = pane(Material.YELLOW_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) != null) continue;
            // 跳过交互槽：激活/强化按钮已设置，材料槽 44 留空供放入
            if (i == ACTIVATE_SLOT || i == ENHANCE_BUTTON_SLOT || i == MATERIAL_SLOT) continue;
            // 底部行：暗红装饰条
            if (i >= 45) {
                inv.setItem(i, red);
            } else if (i == 35 || i == 43) {
                // 材料槽(44)周围：黄色突出
                inv.setItem(i, yellow);
            } else {
                inv.setItem(i, black);
            }
        }
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }
}
