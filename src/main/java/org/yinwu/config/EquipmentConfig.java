package org.yinwu.config;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class EquipmentConfig {
    private boolean enabled;
    private Map<String, Double> armorChance;
    private Map<String, Double> weaponChance;
    private Map<String, Integer> enchantmentLevels;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Map<String, Double> getArmorChance() { return armorChance; }
    public void setArmorChance(Map<String, Double> armorChance) { this.armorChance = armorChance; }
    public Map<String, Double> getWeaponChance() { return weaponChance; }
    public void setWeaponChance(Map<String, Double> weaponChance) { this.weaponChance = weaponChance; }
    public Map<String, Integer> getEnchantmentLevels() { return enchantmentLevels; }
    public void setEnchantmentLevels(Map<String, Integer> enchantmentLevels) { this.enchantmentLevels = enchantmentLevels; }

    /** 从配置段加载 */
    public static EquipmentConfig from(ConfigurationSection s) {
        if (s == null) return null;
        EquipmentConfig c = new EquipmentConfig();
        c.setEnabled(s.getBoolean("enabled", true));
        ConfigurationSection armor = s.getConfigurationSection("armor-chance");
        if (armor != null) {
            Map<String, Double> m = new HashMap<>();
            for (String k : armor.getKeys(false)) m.put(k, armor.getDouble(k, 0.5));
            c.setArmorChance(m);
        }
        ConfigurationSection weapon = s.getConfigurationSection("weapon-chance");
        if (weapon != null) {
            Map<String, Double> m = new HashMap<>();
            for (String k : weapon.getKeys(false)) m.put(k, weapon.getDouble(k, 0.5));
            c.setWeaponChance(m);
        }
        ConfigurationSection ench = s.getConfigurationSection("enchantment-levels");
        if (ench != null) {
            Map<String, Integer> m = new HashMap<>();
            for (String k : ench.getKeys(false)) m.put(k, ench.getInt(k, 2));
            c.setEnchantmentLevels(m);
        }
        return c;
    }
}
