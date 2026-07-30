package org.yinwu.raid;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.util.MythicMobsIntegration;

import java.util.concurrent.ThreadLocalRandom;

public class RaidMobSpawner {
    private final YinwuRaidPlugin plugin;
    private final RaidMobManager mobManager;
    private final SpecialRaidListener listener;
    private final ConfigManager configManager;
    private final MythicMobsIntegration mythicMobs;

    public RaidMobSpawner(YinwuRaidPlugin plugin, RaidMobManager mobManager, SpecialRaidListener listener, MythicMobsIntegration mythicMobs) {
        this.plugin = plugin; this.mobManager = mobManager; this.listener = listener;
        this.configManager = plugin.getConfigManager(); this.mythicMobs = mythicMobs;
    }

    public LivingEntity spawnRaidMob(Location center, int doomLevel, int wave, int totalWaves) {
        Location loc = findValidSpawnLocation(center);
        if (loc == null) return null;
        EntityType type = selectMobType(doomLevel, wave);
        World w = loc.getWorld();
        LivingEntity entity = (LivingEntity) w.spawnEntity(loc, type);
        if (entity != null) {
            mobManager.trackMob(entity);
            applyWaveScaling(entity, doomLevel, wave, totalWaves);
        }
        return entity;
    }

    public Location findValidSpawnLocation(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cz = center.getBlockZ();
        int radius = 24 + ThreadLocalRandom.current().nextInt(16);
        for (int i = 0; i < 30; i++) {
            int x = cx + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = cz + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int y = w.getHighestBlockYAt(x, z);
            if (y > -64 && y < 320) {
                Location loc = new Location(w, x + 0.5, y + 1, z + 0.5);
                if (loc.getBlock().getType() == Material.AIR && loc.getBlock().getRelative(0, -1, 0).getType().isSolid())
                    return loc;
            }
        }
        return null;
    }

    private EntityType selectMobType(int doomLevel, int wave) {
        var r = ThreadLocalRandom.current();
        return switch (doomLevel) {
            case 6 -> r.nextBoolean() ? EntityType.VINDICATOR : EntityType.PILLAGER;
            case 7 -> r.nextBoolean() ? EntityType.RAVAGER : EntityType.VINDICATOR;
            case 8 -> r.nextBoolean() ? EntityType.EVOKER : EntityType.RAVAGER;
            case 9, 10 -> r.nextBoolean() ? EntityType.WITCH : EntityType.EVOKER;
            default -> EntityType.PILLAGER;
        };
    }

    private void applyWaveScaling(LivingEntity entity, int doomLevel, int wave, int totalWaves) {
        double scale = 1.0 + (doomLevel - 6) * 0.2 + (double) wave / totalWaves * 0.3;
        entity.setMaxHealth(entity.getMaxHealth() * scale);
        entity.setHealth(entity.getMaxHealth());
    }

    public void spawnWaveMobs(Location center, int doomLevel, int radius, java.util.List<String> mobTypes, RaidState raidState) {
        // 每波额外生成一个幻术师
        Location illLoc = findValidSpawnLocation(center);
        if (illLoc != null) {
            LivingEntity ill = (LivingEntity) illLoc.getWorld().spawnEntity(illLoc, EntityType.ILLUSIONER);
            if (ill != null) {
                mobManager.trackMob(ill);
                applyWaveScaling(ill, doomLevel, raidState.currentWave, raidState.totalWaves);
            }
        }
        for (int i = 0; i < raidState.mobsPerWave; i++) {
            spawnRaidMob(center, doomLevel, raidState.currentWave, raidState.totalWaves);
        }
    }
}
