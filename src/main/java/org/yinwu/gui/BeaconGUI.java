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
        if (bc != null) {
            Map<Integer, LayerConfig> layers = bc.getLayers();
            if (layers != null) {
                for (Map.Entry<Integer, LayerConfig> e : layers.entrySet()) {
                    int layer = e.getKey();
                    int slot = getSlotForLevel(layer);
                    // 只显示已完整构建的连续层（getBeaconLevel 从底层连续检测）；
                    // 未构建的层及其上方层用玻璃板（fillFrame）代替，不显示图标
                    if (slot >= 0 && layer <= detectedLevel) {
                        setLayerItem(gui, slot, e.getValue(), layer);
                    }
                }
            }
        }
        setEnhanceButton(gui);
        setActivationButton(gui, bc);
        // 槽 44 材料槽留空供放入（fillFrame 跳过，保证可放入/可取出）
        fillFrame(gui);
        player.openInventory(gui);
    }

    /** 合并后的激活按钮（槽 53，材料槽正下方），不介绍彩蛋 */
    private void setActivationButton(Inventory inv, BeaconConfig bc) {
        ItemStack btn = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = btn.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§l激活灾厄信标");
            List<String> lore = new ArrayList<>();
            lore.add("§7先在槽位 §e44 §7放入 §f下界之星");
            lore.add("§7或不祥之瓶后点击本格激活");
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

        // 合并激活按钮：按下界之星 = 普通激活，放不详之瓶 = 彩蛋激活
        if (slot == ACTIVATE_SLOT) {
            ItemStack mat = inv.getItem(MATERIAL_SLOT);
            boolean easter = mat != null && mat.getType() == Material.OMINOUS_BOTTLE;
            listener.tryActivateBeacon(player, easter);
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

    private void setLayerItem(Inventory inv, int slot, LayerConfig lc, int level) {
        Material mat;
        try { mat = Material.valueOf(lc.getMaterial()); } catch (IllegalArgumentException e) { mat = Material.IRON_BLOCK; }
        ItemStack item = new ItemStack(mat, Math.min(level, 64));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6第 " + level + " 层");
            List<String> lore = new ArrayList<>();
            lore.add("§7方块: " + lc.getMaterial());
            lore.add("§7尺寸: " + lc.getSize() + "x" + lc.getSize());
            lore.add("§7已放置: §a✓");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        inv.setItem(slot, item);
    }

    private void fillFrame(Inventory inv) {
        ItemStack frame = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fm = frame.getItemMeta();
        if (fm != null) { fm.setDisplayName(" "); frame.setItemMeta(fm); }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                // 跳过交互槽：激活/强化按钮已设置，材料槽 44 留空供放入
                if (i == ACTIVATE_SLOT || i == ENHANCE_BUTTON_SLOT || i == MATERIAL_SLOT) continue;
                inv.setItem(i, frame);
            }
        }
    }

    /** 中央纵列（从下到上）：铁砧(强化) → 铁块 → 金块 → 钻石块 → 下界合金块 */
    private int getSlotForLevel(int level) {
        return switch (level) {
            case 1 -> 40; case 2 -> 31; case 3 -> 22; case 4 -> 13;
            default -> -1;
        };
    }
}
