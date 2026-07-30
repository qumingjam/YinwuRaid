package org.yinwu.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class LootConfig {
    private int emeraldBaseAmount;
    private int expBottleMultiplier;
    private double enchantedGoldenAppleChance;
    private Map<Integer, List<LevelLootEntry>> perLevelLoot;

    public int getEmeraldBaseAmount() { return emeraldBaseAmount; }
    public void setEmeraldBaseAmount(int emeraldBaseAmount) { this.emeraldBaseAmount = emeraldBaseAmount; }
    public int getExpBottleMultiplier() { return expBottleMultiplier; }
    public void setExpBottleMultiplier(int expBottleMultiplier) { this.expBottleMultiplier = expBottleMultiplier; }
    public double getEnchantedGoldenAppleChance() { return enchantedGoldenAppleChance; }
    public void setEnchantedGoldenAppleChance(double enchantedGoldenAppleChance) { this.enchantedGoldenAppleChance = enchantedGoldenAppleChance; }
    public Map<Integer, List<LevelLootEntry>> getPerLevelLoot() { return perLevelLoot; }
    public void setPerLevelLoot(Map<Integer, List<LevelLootEntry>> perLevelLoot) { this.perLevelLoot = perLevelLoot; }

    /** 从 raid/config.yml raid-loot 段加载 */
    @SuppressWarnings("unchecked")
    public static LootConfig from(ConfigurationSection s) {
        LootConfig c = new LootConfig();
        if (s == null) return c;

        ConfigurationSection base = s.getConfigurationSection("base-rewards");
        if (base != null) {
            c.setEmeraldBaseAmount(base.getInt("emerald-base", 10));
            c.setExpBottleMultiplier(base.getInt("exp-bottle-multiplier", 2));
        }

        ConfigurationSection extra = s.getConfigurationSection("extra-items");
        if (extra != null) {
            ConfigurationSection apple = extra.getConfigurationSection("enchanted-golden-apple");
            if (apple != null) c.setEnchantedGoldenAppleChance(apple.getDouble("chance", 0.3));
        }

        ConfigurationSection per = s.getConfigurationSection("per-level");
        if (per != null) {
            Map<Integer, List<LevelLootEntry>> map = new HashMap<>();
            for (String lk : per.getKeys(false)) {
                try {
                    int lv = Integer.parseInt(lk);
                    List<Map<?, ?>> raw = per.getMapList(lk);
                    List<LevelLootEntry> list = new ArrayList<>();
                    for (Map<?, ?> r : raw) list.add(LevelLootEntry.from(r));
                    map.put(lv, list);
                } catch (NumberFormatException ignored) {}
            }
            c.setPerLevelLoot(map);
        }
        return c;
    }
}
