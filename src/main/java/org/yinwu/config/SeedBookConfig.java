package org.yinwu.config;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

public class SeedBookConfig {
    private String title;
    private String author;
    private List<String> pages;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public List<String> getPages() { return pages; }
    public void setPages(List<String> pages) { this.pages = pages; }

    /** 从配置段加载 */
    public static SeedBookConfig from(ConfigurationSection s) {
        if (s == null) return null;
        SeedBookConfig c = new SeedBookConfig();
        c.setTitle(s.getString("title", ""));
        c.setAuthor(s.getString("author", ""));
        c.setPages(s.getStringList("pages"));
        return c;
    }
}
