package org.yinwu.api;

import net.yinwu.lib.api.RaidAPI;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.raid.RaidState;
import org.yinwu.raid.SpecialRaidListener;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RaidAPIImpl implements RaidAPI {

    private final YinwuRaidPlugin plugin;
    private final SpecialRaidListener raidListener;

    public RaidAPIImpl(YinwuRaidPlugin plugin, SpecialRaidListener raidListener) {
        this.plugin = plugin;
        this.raidListener = raidListener;
    }

    @Override
    public String apiVersion() {
        return "1.0.0";
    }

    @Override
    public boolean isInRaid(Player player) {
        RaidState state = raidListener.getRaidStates().get(player.getUniqueId());
        return state != null && state.isActive;
    }

    @Override
    public int getRaidLevel(Player player) {
        RaidState state = raidListener.getRaidStates().get(player.getUniqueId());
        return state != null && state.isActive ? state.originalDoomLevel : 0;
    }

    @Override
    public int getCurrentWave(Player player) {
        RaidState state = raidListener.getRaidStates().get(player.getUniqueId());
        return state != null && state.isActive ? state.currentWave : 0;
    }

    @Override
    public int getTotalWaves(Player player) {
        RaidState state = raidListener.getRaidStates().get(player.getUniqueId());
        return state != null && state.isActive ? state.totalWaves : 0;
    }

    @Override
    public boolean isRaidMob(Entity entity) {
        for (RaidState state : raidListener.getRaidStates().values()) {
            if (state.isActive && state.raidMobs.contains(entity.getUniqueId())) return true;
        }
        return false;
    }

    @Override
    public boolean isRaidBoss(Entity entity) {
        return entity != null && entity.getPersistentDataContainer()
            .has(new org.bukkit.NamespacedKey(plugin, "raid_boss"));
    }

    @Override
    public List<String> getActiveRaidPlayers() {
        List<String> names = new ArrayList<>();
        for (RaidState state : raidListener.getRaidStates().values()) {
            if (state.isActive) {
                Player p = plugin.getServer().getPlayer(state.playerId);
                if (p != null) names.add(p.getName());
            }
        }
        return names;
    }

    @Override
    public boolean hasRaidBonus(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        var meta = item.getItemMeta();
        if (meta == null) return false;
        var key = new org.bukkit.NamespacedKey(plugin, "forge_raid_bonus");
        return key != null && meta.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE);
    }
}
