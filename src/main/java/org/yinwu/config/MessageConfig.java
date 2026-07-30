package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class MessageConfig {
    private boolean prefixEnabled;
    private String prefix;
    private boolean soundEnabled;
    private boolean actionbarEnabled;
    private boolean titleEnabled;

    public boolean isPrefixEnabled() { return prefixEnabled; }
    public void setPrefixEnabled(boolean prefixEnabled) { this.prefixEnabled = prefixEnabled; }
    public String getPrefix() { return prefix; }
    public void setPrefix(String prefix) { this.prefix = prefix; }
    public boolean isSoundEnabled() { return soundEnabled; }
    public void setSoundEnabled(boolean soundEnabled) { this.soundEnabled = soundEnabled; }
    public boolean isActionbarEnabled() { return actionbarEnabled; }
    public void setActionbarEnabled(boolean actionbarEnabled) { this.actionbarEnabled = actionbarEnabled; }
    public boolean isTitleEnabled() { return titleEnabled; }
    public void setTitleEnabled(boolean titleEnabled) { this.titleEnabled = titleEnabled; }

    /** 从配置段加载 */
    public static MessageConfig from(ConfigurationSection s) {
        MessageConfig c = new MessageConfig();
        if (s == null) return c;
        c.setPrefixEnabled(s.getBoolean("prefix-enabled", true));
        c.setPrefix(s.getString("prefix", "§6§l[YinwuRaid] §r"));
        c.setSoundEnabled(s.getBoolean("sound-enabled", true));
        c.setActionbarEnabled(s.getBoolean("actionbar-enabled", true));
        c.setTitleEnabled(s.getBoolean("title-enabled", true));
        return c;
    }
}
