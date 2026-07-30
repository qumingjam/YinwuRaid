package org.yinwu.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public class BeaconConfig {
    private boolean enabled;
    private int maxRange;
    private int doomEffectDuration;
    private boolean requireContainer;
    private List<String> containerTypes;
    private Map<Integer, LayerConfig> layers;
    private Map<Integer, Integer> doomLevels;
    private ActivationConfig activationConfig;
    private ActivationConfig easterEggActivationConfig;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxRange() { return maxRange; }
    public void setMaxRange(int maxRange) { this.maxRange = maxRange; }
    public int getDoomEffectDuration() { return doomEffectDuration; }
    public void setDoomEffectDuration(int doomEffectDuration) { this.doomEffectDuration = doomEffectDuration; }
    public boolean isRequireContainer() { return requireContainer; }
    public void setRequireContainer(boolean requireContainer) { this.requireContainer = requireContainer; }
    public List<String> getContainerTypes() { return containerTypes; }
    public void setContainerTypes(List<String> containerTypes) { this.containerTypes = containerTypes; }
    public Map<Integer, LayerConfig> getLayers() { return layers; }
    public void setLayers(Map<Integer, LayerConfig> layers) { this.layers = layers; }
    public Map<Integer, Integer> getDoomLevels() { return doomLevels; }
    public void setDoomLevels(Map<Integer, Integer> doomLevels) { this.doomLevels = doomLevels; }
    public ActivationConfig getActivationConfig() { return activationConfig; }
    public void setActivationConfig(ActivationConfig activationConfig) { this.activationConfig = activationConfig; }
    public ActivationConfig getEasterEggActivationConfig() { return easterEggActivationConfig; }
    public void setEasterEggActivationConfig(ActivationConfig easterEggActivationConfig) { this.easterEggActivationConfig = easterEggActivationConfig; }

    /** 从 raid/config.yml beacon 段加载 */
    public static BeaconConfig from(ConfigurationSection s) {
        BeaconConfig c = new BeaconConfig();
        if (s == null) return c;
        c.setEnabled(s.getBoolean("enabled", true));
        c.setMaxRange(s.getInt("max-range", 50));
        c.setDoomEffectDuration(s.getInt("doom-effect-duration", 300));

        ConfigurationSection det = s.getConfigurationSection("detection");
        if (det != null) {
            c.setRequireContainer(det.getBoolean("require-container", true));
            c.setContainerTypes(det.getStringList("container-types"));
        }

        ConfigurationSection ly = s.getConfigurationSection("layers");
        if (ly != null) {
            Map<Integer, LayerConfig> map = new HashMap<>();
            for (String k : ly.getKeys(false)) {
                try { map.put(Integer.parseInt(k), LayerConfig.from(ly.getConfigurationSection(k))); }
                catch (NumberFormatException ignored) {}
            }
            c.setLayers(map);
        }

        ConfigurationSection dl = s.getConfigurationSection("doom-levels");
        if (dl != null) {
            Map<Integer, Integer> map = new HashMap<>();
            for (String k : dl.getKeys(false)) {
                try { map.put(Integer.parseInt(k), dl.getInt(k)); }
                catch (NumberFormatException ignored) {}
            }
            c.setDoomLevels(map);
        }

        c.setActivationConfig(ActivationConfig.from(s.getConfigurationSection("activation")));
        c.setEasterEggActivationConfig(ActivationConfig.from(s.getConfigurationSection("easter-egg-activation")));
        return c;
    }
}
