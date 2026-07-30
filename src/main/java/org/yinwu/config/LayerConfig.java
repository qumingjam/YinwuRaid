package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class LayerConfig {
    private String material;
    private int size;
    private int offset;

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public int getSize() { return size; }
    public void setSize(int size) { this.size = size; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = offset; }

    /** 从配置段加载 */
    public static LayerConfig from(ConfigurationSection s) {
        if (s == null) return null;
        LayerConfig c = new LayerConfig();
        c.setMaterial(s.getString("material", "IRON_BLOCK"));
        c.setSize(s.getInt("size", 3));
        c.setOffset(s.getInt("offset", 1));
        return c;
    }
}
