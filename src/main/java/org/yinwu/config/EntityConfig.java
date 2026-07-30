package org.yinwu.config;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class EntityConfig {
    private double healthMultiplier;
    private double damageMultiplier;
    private double speedMultiplier;
    private int followRange;
    private VillagerGiftConfig villagerGift;
    private DefenderConfig villageDefender;
    private Map<String, MobTargetConfig> mobTargets;
    private EquipmentConfig equipment;

    public double getHealthMultiplier() { return healthMultiplier; }
    public void setHealthMultiplier(double healthMultiplier) { this.healthMultiplier = healthMultiplier; }
    public double getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(double damageMultiplier) { this.damageMultiplier = damageMultiplier; }
    public double getSpeedMultiplier() { return speedMultiplier; }
    public void setSpeedMultiplier(double speedMultiplier) { this.speedMultiplier = speedMultiplier; }
    public int getFollowRange() { return followRange; }
    public void setFollowRange(int followRange) { this.followRange = followRange; }
    public VillagerGiftConfig getVillagerGift() { return villagerGift; }
    public void setVillagerGift(VillagerGiftConfig villagerGift) { this.villagerGift = villagerGift; }
    public DefenderConfig getVillageDefender() { return villageDefender; }
    public void setVillageDefender(DefenderConfig villageDefender) { this.villageDefender = villageDefender; }
    public Map<String, MobTargetConfig> getMobTargets() { return mobTargets; }
    public void setMobTargets(Map<String, MobTargetConfig> mobTargets) { this.mobTargets = mobTargets; }
    public EquipmentConfig getEquipment() { return equipment; }
    public void setEquipment(EquipmentConfig equipment) { this.equipment = equipment; }

    /** 从 raid/config.yml entities 段加载 */
    public static EntityConfig from(ConfigurationSection s) {
        EntityConfig c = new EntityConfig();
        if (s == null) {
            c.setHealthMultiplier(2.0); c.setDamageMultiplier(1.5);
            c.setSpeedMultiplier(1.2); c.setFollowRange(48);
            c.setVillagerGift(VillagerGiftConfig.from(null));
            return c;
        }
        c.setHealthMultiplier(s.getDouble("health-multiplier", 2.0));
        c.setDamageMultiplier(s.getDouble("damage-multiplier", 1.5));
        c.setSpeedMultiplier(s.getDouble("speed-multiplier", 1.2));
        c.setFollowRange(s.getInt("follow-range", 48));

        c.setVillagerGift(VillagerGiftConfig.from(s.getConfigurationSection("villager-gift")));
        c.setVillageDefender(DefenderConfig.from(s.getConfigurationSection("village-defender")));

        ConfigurationSection tg = s.getConfigurationSection("mob-targets");
        if (tg != null) {
            Map<String, MobTargetConfig> map = new HashMap<>();
            for (String k : tg.getKeys(false)) {
                MobTargetConfig mt = MobTargetConfig.from(tg.getConfigurationSection(k));
                if (mt != null) map.put(k, mt);
            }
            c.setMobTargets(map);
        }

        c.setEquipment(EquipmentConfig.from(s.getConfigurationSection("equipment")));
        return c;
    }
}
