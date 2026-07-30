package org.yinwu.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class EnhancementConfig {
    private int maxEnhanceCount;
    private List<String> blacklist;
    private Map<String, Integer> limits;
    private Map<String, Map<Integer, Double>> seedChances;
    private Map<String, String> seedNames;
    private Map<String, Map<Integer, Integer>> enchantCounts;
    private Map<String, SeedBookConfig> seedBooks;
    private Map<String, Object> rules;

    public int getMaxEnhanceCount() { return maxEnhanceCount; }
    public void setMaxEnhanceCount(int maxEnhanceCount) { this.maxEnhanceCount = maxEnhanceCount; }
    public List<String> getBlacklist() { return blacklist; }
    public void setBlacklist(List<String> blacklist) { this.blacklist = blacklist; }
    public Map<String, Integer> getLimits() { return limits; }
    public void setLimits(Map<String, Integer> limits) { this.limits = limits; }
    public Map<String, Map<Integer, Double>> getSeedChances() { return seedChances; }
    public void setSeedChances(Map<String, Map<Integer, Double>> seedChances) { this.seedChances = seedChances; }
    public Map<String, String> getSeedNames() { return seedNames; }
    public void setSeedNames(Map<String, String> seedNames) { this.seedNames = seedNames; }
    public Map<String, Map<Integer, Integer>> getEnchantCounts() { return enchantCounts; }
    public void setEnchantCounts(Map<String, Map<Integer, Integer>> enchantCounts) { this.enchantCounts = enchantCounts; }
    public Map<String, SeedBookConfig> getSeedBooks() { return seedBooks; }
    public void setSeedBooks(Map<String, SeedBookConfig> seedBooks) { this.seedBooks = seedBooks; }
    public Map<String, Object> getRules() { return rules; }
    public void setRules(Map<String, Object> rules) { this.rules = rules; }

    /** 从 rewards/config.yml enhancement 段加载 */
    public static EnhancementConfig from(ConfigurationSection s) {
        EnhancementConfig c = new EnhancementConfig();
        if (s == null) return c;
        c.setMaxEnhanceCount(s.getInt("max-enhance-count", 3));
        c.setBlacklist(s.getStringList("blacklist"));

        ConfigurationSection lm = s.getConfigurationSection("limits");
        if (lm != null) {
            Map<String, Integer> map = new HashMap<>();
            for (String k : lm.getKeys(false)) map.put(k, lm.getInt(k));
            c.setLimits(map);
        }

        ConfigurationSection seeds = s.getConfigurationSection("seeds");
        if (seeds != null) {
            Map<String, Map<Integer, Double>> chances = new HashMap<>();
            Map<String, String> names = new HashMap<>();
            Map<String, SeedBookConfig> books = new HashMap<>();
            for (String key : seeds.getKeys(false)) {
                ConfigurationSection sd = seeds.getConfigurationSection(key);
                if (sd == null) continue;
                names.put(key, sd.getString("name", ""));
                books.put(key, SeedBookConfig.from(sd.getConfigurationSection("book")));
                ConfigurationSection ch = sd.getConfigurationSection("chances");
                if (ch != null) {
                    Map<Integer, Double> cm = new HashMap<>();
                    for (String ck : ch.getKeys(false)) {
                        try { cm.put(Integer.parseInt(ck), ch.getDouble(ck)); }
                        catch (NumberFormatException ignored) {}
                    }
                    chances.put(key, cm);
                }
            }
            c.setSeedChances(chances);
            c.setSeedNames(names);
            c.setSeedBooks(books);
        }

        ConfigurationSection ec = s.getConfigurationSection("enchant-counts");
        if (ec != null) {
            Map<String, Map<Integer, Integer>> map = new HashMap<>();
            for (String prof : ec.getKeys(false)) {
                ConfigurationSection ps = ec.getConfigurationSection(prof);
                if (ps == null) continue;
                Map<Integer, Integer> lv = new HashMap<>();
                for (String lk : ps.getKeys(false)) {
                    try { lv.put(Integer.parseInt(lk), ps.getInt(lk)); }
                    catch (NumberFormatException ignored) {}
                }
                map.put(prof, lv);
            }
            c.setEnchantCounts(map);
        }
        return c;
    }
}
