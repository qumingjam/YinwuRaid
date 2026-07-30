package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class MobTargetConfig {
    private boolean villagerPriority;
    private int priority;

    public boolean isVillagerPriority() { return villagerPriority; }
    public void setVillagerPriority(boolean villagerPriority) { this.villagerPriority = villagerPriority; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    /** 从配置段加载 */
    public static MobTargetConfig from(ConfigurationSection s) {
        if (s == null) return null;
        MobTargetConfig c = new MobTargetConfig();
        c.setVillagerPriority(s.getBoolean("villager-priority", false));
        c.setPriority(s.getInt("priority", 2));
        return c;
    }
}
