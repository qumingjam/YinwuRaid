package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class TitleTimingConfig {
    private int firstFadeIn;
    private int firstDisplay;
    private int firstFadeOut;
    private int secondFadeIn;
    private int secondDisplay;
    private int secondFadeOut;
    private long delayBetween;

    public int getFirstFadeIn() { return firstFadeIn; }
    public void setFirstFadeIn(int firstFadeIn) { this.firstFadeIn = firstFadeIn; }
    public int getFirstDisplay() { return firstDisplay; }
    public void setFirstDisplay(int firstDisplay) { this.firstDisplay = firstDisplay; }
    public int getFirstFadeOut() { return firstFadeOut; }
    public void setFirstFadeOut(int firstFadeOut) { this.firstFadeOut = firstFadeOut; }
    public int getSecondFadeIn() { return secondFadeIn; }
    public void setSecondFadeIn(int secondFadeIn) { this.secondFadeIn = secondFadeIn; }
    public int getSecondDisplay() { return secondDisplay; }
    public void setSecondDisplay(int secondDisplay) { this.secondDisplay = secondDisplay; }
    public int getSecondFadeOut() { return secondFadeOut; }
    public void setSecondFadeOut(int secondFadeOut) { this.secondFadeOut = secondFadeOut; }
    public long getDelayBetween() { return delayBetween; }
    public void setDelayBetween(long delayBetween) { this.delayBetween = delayBetween; }

    /** 从配置段加载 */
    public static TitleTimingConfig from(ConfigurationSection s) {
        if (s == null) return null;
        TitleTimingConfig c = new TitleTimingConfig();
        c.setFirstFadeIn(s.getInt("first-fade-in", 10));
        c.setFirstDisplay(s.getInt("first-display", 60));
        c.setFirstFadeOut(s.getInt("first-fade-out", 20));
        c.setSecondFadeIn(s.getInt("second-fade-in", 10));
        c.setSecondDisplay(s.getInt("second-display", 100));
        c.setSecondFadeOut(s.getInt("second-fade-out", 20));
        c.setDelayBetween(s.getLong("delay-between", 80));
        return c;
    }
}
