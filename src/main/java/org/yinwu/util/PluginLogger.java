package org.yinwu.util;

import org.yinwu.YinwuRaidPlugin;

/**
 * 统一的日志工具类
 * 提供格式化的日志记录功能
 */
public class PluginLogger {

    private final java.util.logging.Logger logger;
    private final String pluginName;
    private final YinwuRaidPlugin plugin;

    public PluginLogger(YinwuRaidPlugin plugin) {
        this.logger = plugin.getLogger();
        this.pluginName = plugin.getName();
        this.plugin = plugin;
    }

    /**
     * 记录信息日志
     */
    public void info(String message) {
        logger.info("[" + pluginName + "] " + message);
    }

    /**
     * 记录警告日志
     */
    public void warning(String message) {
        logger.warning("[" + pluginName + "] §e" + message);
    }

    /**
     * 记录严重错误日志
     */
    public void severe(String message) {
        logger.severe("[" + pluginName + "] §c" + message);
    }

    /**
     * 记录详细日志（仅在调试模式启用时输出）
     */
    public void fine(String message) {
        logger.fine("[" + pluginName + "] §7" + message);
    }

    /**
     * 记录调试日志（需要配置中启用 debug.enabled）
     */
    public void debug(String message) {
        // ✅ 使用 ConfigManager 检查调试模式
        org.yinwu.config.ConfigManager configManager = plugin.getConfigManager();
        if (configManager != null && configManager.isDebugEnabled()) {
            logger.info("[" + pluginName + "] §e[DEBUG] " + message);
        }
    }

    /**
     * 记录成功日志
     */
    public void success(String message) {
        logger.info("[" + pluginName + "] §a✓ " + message);
    }

    /**
     * 记录错误日志，可选择是否打印堆栈跟踪
     */
    public void error(String message, Throwable e) {
        if (e != null) {
            logger.log(java.util.logging.Level.SEVERE, "[" + pluginName + "] §c✗ " + message, e);
        } else {
            logger.severe("[" + pluginName + "] §c✗ " + message);
        }
    }

    /**
     * 记录错误日志（不带堆栈跟踪）
     */
    public void error(String message) {
        error(message, null);
    }
}
