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

    public void openBeaconGUI(Player player) {
        Inventory gui = plugin.getServer().createInventory(null, 54, "§4§l灾厄信标");
        BeaconConfig bc = configManager.getBeaconConfig();
        if (bc != null) {
            Map<Integer, LayerConfig> layers = bc.getLayers();
            if (layers != null) {
                for (Map.Entry<Integer, LayerConfig> e : layers.entrySet()) {
                    int slot = getSlotForLevel(e.getKey());
                    if (slot >= 0) setLayerItem(gui, slot, e.getValue(), e.getKey());
                }
            }
        }
        fillFrame(gui);
        player.openInventory(gui);
    }

    public boolean handleClick(Player player, int slot, Inventory inv) {
        BeaconConfig bc = configManager.getBeaconConfig();
        if (bc == null) return false;

        // Check if it's an activation slot (normal or easter egg)
        ActivationConfig ac = bc.getActivationConfig();
        if (slot == 22 && ac != null) { listener.tryActivateBeacon(player, false); return true; }
        ActivationConfig ee = bc.getEasterEggActivationConfig();
        if (slot == 31 && ee != null) { listener.tryActivateBeacon(player, true); return true; }
        return false;
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
            if (inv.getItem(i) == null) inv.setItem(i, frame);
        }
    }

    private int getSlotForLevel(int level) {
        return switch (level) {
            case 1 -> 21; case 2 -> 23; case 3 -> 30; case 4 -> 32;
            default -> -1;
        };
    }
}
