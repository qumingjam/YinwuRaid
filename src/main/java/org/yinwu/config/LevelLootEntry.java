package org.yinwu.config;

import java.util.Map;

public class LevelLootEntry {
    private String material;
    private int amount;
    private String enchant;
    private int enchantLevel;
    private int seedType;

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public String getEnchant() { return enchant; }
    public void setEnchant(String enchant) { this.enchant = enchant; }
    public int getEnchantLevel() { return enchantLevel; }
    public void setEnchantLevel(int enchantLevel) { this.enchantLevel = enchantLevel; }
    public int getSeedType() { return seedType; }
    public void setSeedType(int seedType) { this.seedType = seedType; }

    /** 从原始 Map 加载（Yaml getMapList 格式） */
    @SuppressWarnings("unchecked")
    public static LevelLootEntry from(Map<?, ?> raw) {
        LevelLootEntry e = new LevelLootEntry();
        Object material = raw.get("material");
        e.setMaterial(material != null ? material.toString() : "AIR");
        Object amount = raw.get("amount");
        e.setAmount(amount instanceof Number ? ((Number) amount).intValue() : 1);
        Object enchant = raw.get("enchant");
        if (enchant != null) e.setEnchant(enchant.toString());
        Object enchLevel = raw.get("enchant-level");
        if (enchLevel instanceof Number) e.setEnchantLevel(((Number) enchLevel).intValue());
        Object seedType = raw.get("seed-type");
        if (seedType instanceof Number) e.setSeedType(((Number) seedType).intValue());
        return e;
    }
}
