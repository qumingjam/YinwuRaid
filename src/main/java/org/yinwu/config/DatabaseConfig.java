package org.yinwu.config;

import org.bukkit.configuration.ConfigurationSection;

public class DatabaseConfig {
    private String type;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private boolean mysqlUseSsl;
    private int poolSize;

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getMysqlHost() { return mysqlHost; }
    public void setMysqlHost(String mysqlHost) { this.mysqlHost = mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public void setMysqlPort(int mysqlPort) { this.mysqlPort = mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public void setMysqlDatabase(String mysqlDatabase) { this.mysqlDatabase = mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public void setMysqlUsername(String mysqlUsername) { this.mysqlUsername = mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public void setMysqlPassword(String mysqlPassword) { this.mysqlPassword = mysqlPassword; }
    public boolean isMysqlUseSsl() { return mysqlUseSsl; }
    public void setMysqlUseSsl(boolean mysqlUseSsl) { this.mysqlUseSsl = mysqlUseSsl; }
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int poolSize) { this.poolSize = poolSize; }

    /** 从配置段加载 */
    public static DatabaseConfig from(ConfigurationSection s) {
        DatabaseConfig c = new DatabaseConfig();
        if (s == null) return c;
        c.setType(s.getString("type", "H2"));
        ConfigurationSection mysql = s.getConfigurationSection("mysql");
        if (mysql != null) {
            c.setMysqlHost(mysql.getString("host", "localhost"));
            c.setMysqlPort(mysql.getInt("port", 3306));
            c.setMysqlDatabase(mysql.getString("database", "yinwuraids"));
            c.setMysqlUsername(mysql.getString("username", "root"));
            c.setMysqlPassword(mysql.getString("password", "password"));
            c.setMysqlUseSsl(mysql.getBoolean("use-ssl", false));
        }
        c.setPoolSize(s.getInt("pool-size", 10));
        return c;
    }
}
