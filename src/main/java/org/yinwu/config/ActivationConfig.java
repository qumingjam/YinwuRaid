package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class ActivationConfig {
    private String material;
    private int amount;
    private String displayName;

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /** 从配置段加载 */
    public static ActivationConfig from(ConfigurationSection s) {
        if (s == null) return null;
        ActivationConfig c = new ActivationConfig();
        c.setMaterial(s.getString("material", "NETHER_STAR"));
        c.setAmount(s.getInt("amount", 1));
        c.setDisplayName(s.getString("display-name", ""));
        return c;
    }
}
