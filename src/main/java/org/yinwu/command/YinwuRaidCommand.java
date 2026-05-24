package org.yinwu.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * YinwuRaid 插件命令处理器
 * 
 * @author Yinwu
 */
public class YinwuRaidCommand implements CommandExecutor, TabCompleter {
    
    private final YinwuRaidPlugin plugin;
    
    public YinwuRaidCommand(YinwuRaidPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 无参数 - 显示帮助信息
        if (args.length == 0) {
            sendHelpMessage(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        // reload 子命令
        if (subCommand.equals("reload")) {
            return handleReload(sender);
        }
        
        // giveitem 子命令
        if (subCommand.equals("giveitem")) {
            return handleGiveItem(sender, args);
        }
        
        // ✅ debug 子命令
        if (subCommand.equals("debug")) {
            return handleDebug(sender, args);
        }
        
        // 未知子命令
        sender.sendMessage("§c[灾厄袭击] §7未知命令！使用 §e/yinwuraid §7查看帮助");
        return true;
    }
    
    /**
     * 处理 reload 命令
     */
    private boolean handleReload(CommandSender sender) {
        // 检查权限
        if (!sender.hasPermission("yinwuraid.admin.reload")) {
            sender.sendMessage("§c[灾厄袭击] §7你没有权限执行此命令！");
            return true;
        }
        
        try {
            // ✅ 重新加载配置管理器（清除所有缓存并重新加载）
            plugin.getConfigManager().reload();
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 重载: 配置管理器已重新加载");
            }
            
            // ✅ 重新初始化附魔规则管理器
            org.yinwu.beacon.EnchantmentRuleManager.initialize(plugin);
            if (plugin.getConfigManager().isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 重载: 附魔规则管理器已重新初始化");
            }
            
            // ✅ 重新加载灾厄袭击配置（从 ConfigManager 获取最新值）
            if (plugin.getSpecialRaidListener() != null) {
                plugin.getSpecialRaidListener().reload();
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 重载: 袭击监听器已重新加载");
                }
            }
            
            // 重新加载奖励配置
            if (plugin.getRewardManager() != null) {
                plugin.getRewardManager().reload();
                if (plugin.getConfigManager().isDebugEnabled()) {
                    plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 重载: 奖励管理器已重新加载");
                }
            }
            
            // ✅ 验证灾厄之种概率配置是否正确加载
            ConfigManager.EnhancementConfig enhancementConfig = plugin.getConfigManager().getEnhancementConfig();
            if (enhancementConfig != null) {
                plugin.getLogger().info("§a✓ 强化系统配置已加载（最大强化次数: " + enhancementConfig.getMaxEnhanceCount() + "）");
            }
            
            sender.sendMessage("§a━━━━━━━━━━━━━━━━");
            sender.sendMessage("§6§l[YinwuRaid] §a配置文件已重新加载！");
            sender.sendMessage("§a━━━━━━━━━━━━━━━━");
            
            plugin.getLogger().info("§a✓ 配置文件已由 " + sender.getName() + " 重新加载");
            
        } catch (Exception e) {
            sender.sendMessage("§c[灾厄袭击] §7重新加载失败：" + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "§c✗ 重新加载配置失败", e);
        }
        
        return true;
    }
    
    
    /**
     * 处理 giveitem 命令
     */
    private boolean handleGiveItem(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission("yinwuraid.admin.giveitem")) {
            sender.sendMessage("§c[灾厄袭击] §7你没有权限执行此命令！");
            return true;
        }
        
        // 必须是玩家
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c[灾厄袭击] §7此命令只能由玩家执行！");
            return true;
        }
        
        Player player = (Player) sender;
        
        // 检查参数
        if (args.length < 2) {
            player.sendMessage("§c[灾厄袭击] §7用法：/yinwuraid giveitem <物品ID> [数量]");
            player.sendMessage("§7示例：/yinwuraid giveitem NETHER_STAR 5");
            return true;
        }
        
        String itemName = args[1].toUpperCase();
        int amount = 1;
        
        // 解析数量
        if (args.length >= 3) {
            try {
                amount = Integer.parseInt(args[2]);
                if (amount <= 0 || amount > 64) {
                    player.sendMessage("§c[灾厄袭击] §7数量必须在 1-64 之间！");
                    return true;
                }
            } catch (NumberFormatException e) {
                player.sendMessage("§c[灾厄袭击] §7无效的数量！");
                return true;
            }
        }
        
        // 解析物品 - 只支持灾厄之种
        ItemStack item;
        String displayName = null;
        String itemNameForLog = "";
        String seedKey = null;
        
        // 特殊处理：灾厄之种
        if (itemName.equals("SEED1") || itemName.equals("DISASTER_SEED_1")) {
            item = new ItemStack(Material.WRITTEN_BOOK, amount);
            displayName = "§d灾厄之种I";
            itemNameForLog = displayName;
            seedKey = "seed-1";
        } else if (itemName.equals("SEED2") || itemName.equals("DISASTER_SEED_2")) {
            item = new ItemStack(Material.WRITTEN_BOOK, amount);
            displayName = "§d灾厄之种II";
            itemNameForLog = displayName;
            seedKey = "seed-2";
        } else {
            player.sendMessage("§c[灾厄袭击] §7无效的物品 ID：" + itemName);
            player.sendMessage("§7可用物品：SEED1 (灾厄之种I), SEED2 (灾厄之种II)");
            return true;
        }
        
        // 设置显示名称和成书内容
        if (displayName != null && seedKey != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                
                // 填充成书内容（从配置读取）
                org.yinwu.config.ConfigManager.EnhancementConfig enhancementConfig = 
                    plugin.getConfigManager().getEnhancementConfig();
                if (enhancementConfig != null) {
                    java.util.Map<String, org.yinwu.config.ConfigManager.SeedBookConfig> seedBooks = 
                        enhancementConfig.getSeedBooks();
                    if (seedBooks != null && seedBooks.containsKey(seedKey)) {
                        org.yinwu.config.ConfigManager.SeedBookConfig bookConfig = seedBooks.get(seedKey);
                        if (meta instanceof org.bukkit.inventory.meta.BookMeta) {
                            org.bukkit.inventory.meta.BookMeta bookMeta = (org.bukkit.inventory.meta.BookMeta) meta;
                            if (bookConfig.getTitle() != null && !bookConfig.getTitle().isEmpty()) {
                                bookMeta.setTitle(bookConfig.getTitle());
                            }
                            if (bookConfig.getAuthor() != null && !bookConfig.getAuthor().isEmpty()) {
                                bookMeta.setAuthor(bookConfig.getAuthor());
                            }
                            if (bookConfig.getPages() != null && !bookConfig.getPages().isEmpty()) {
                                bookMeta.setPages(bookConfig.getPages());
                            }
                        }
                    }
                }
                
                item.setItemMeta(meta);
            }
        }
        
        // ✅ Folia 兼容：使用 RegionScheduler 在玩家所在区域线程中添加物品
        org.bukkit.Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
            if (!player.isOnline()) return;
            player.getInventory().addItem(item);
        });
        
        player.sendMessage("§a━━━━━━━━━━━━━━━━");
        player.sendMessage("§6§l[YinwuRaid] §a已获得物品！");
        player.sendMessage("§e 物品：§f" + itemNameForLog);
        player.sendMessage("§e 数量：§f" + amount);
        player.sendMessage("§a━━━━━━━━━━━━━━━━");
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 给予物品: item=" + itemNameForLog + ", player=" + player.getName() + ", amount=" + amount);
        }
        
        plugin.getLogger().info("§a✓ 已给予玩家 " + player.getName() + " " + amount + " x " + itemNameForLog);
        
        return true;
    }
    
    /**
     * ✅ 处理 debug 命令
     */
    private boolean handleDebug(CommandSender sender, String[] args) {
        // 检查权限
        if (!sender.hasPermission("yinwuraid.admin.debug")) {
            sender.sendMessage("§c[灾厄袭击] §7你没有权限执行此命令！");
            return true;
        }
        
        if (args.length < 2) {
            showDebugHelp(sender);
            return true;
        }
        
        String subCommand = args[1].toLowerCase();
        
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 调试子命令: " + subCommand + ", 发送者=" + sender.getName());
        }
        
        switch (subCommand) {
            case "info":
                sendDebugInfo(sender);
                break;
            case "config":
                sendConfigInfo(sender);
                break;
            case "stats":
                showPerformanceStats(sender);
                break;
            case "beacon":
                showBeaconInfo(sender);
                break;
            case "spawn":
                handleDebugSpawn(sender, args);
                break;
            case "trigger":
                handleDebugTrigger(sender, args);
                break;
            case "reloadbeacon":
                handleDebugReloadBeacon(sender);
                break;
            default:
                sender.sendMessage("§c未知的调试子命令！使用 /yinwuraid debug 查看帮助");
        }
        
        return true;
    }
    
    /**
     * ✅ 显示调试帮助信息
     */
    private void showDebugHelp(CommandSender sender) {
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§l[YinwuRaid] §e调试命令帮助");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e/yinwuraid debug info §7- 显示插件基本信息");
        sender.sendMessage("§e/yinwuraid debug config §7- 显示当前配置信息");
        sender.sendMessage("§e/yinwuraid debug stats §7- 显示性能统计信息");
        sender.sendMessage("§e/yinwuraid debug beacon §7- 显示信标状态信息");
        sender.sendMessage("§e/yinwuraid debug spawn <mob> [count] §7- 生成测试怪物");
        sender.sendMessage("§e/yinwuraid debug trigger [level] §7- 手动触发袭击");
        sender.sendMessage("§e/yinwuraid debug reloadbeacon §7- 刷新信标状态");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 发送调试信息
     */
    private void sendDebugInfo(CommandSender sender) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 发送调试信息给 " + sender.getName());
        }
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6[YinwuRaid] §e调试信息");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e插件版本: §f" + plugin.getDescription().getVersion());
        sender.sendMessage("§eJava 版本: §f" + System.getProperty("java.version"));
        sender.sendMessage("§e服务器类型: §f" + (isFolia() ? "Folia" : "Bukkit/Paper"));
        
        if (plugin.getSpecialRaidListener() != null) {
            sender.sendMessage("§e活跃袭击数: §f" + plugin.getSpecialRaidListener().getActiveRaidCount());
        }
        
        if (plugin.getBeaconDetector() != null) {
            org.bukkit.Location lastBeacon = plugin.getBeaconDetector().getLastDetectedBeacon();
            sender.sendMessage("§e最后检测信标: §f" + (lastBeacon != null ? formatLocation(lastBeacon) : "未检测到"));
        }
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 发送配置信息
     */
    private void sendConfigInfo(CommandSender sender) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [YinwuRaidCommand] 发送配置信息给 " + sender.getName());
        }
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6[YinwuRaid] §e配置信息");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getBukkitConfig();
        
        sender.sendMessage("§e插件启用: §f" + config.getBoolean("enabled", true));
        sender.sendMessage("§e调试模式: §f" + config.getBoolean("debug", false));
        sender.sendMessage("§e语言: §f" + config.getString("language", "zh_CN"));
        
        // 信标配置
        sender.sendMessage("§6--- 信标配置 ---");
        org.bukkit.configuration.ConfigurationSection beaconSection = config.getConfigurationSection("beacon");
        if (beaconSection != null) {
            sender.sendMessage("§e信标启用: §f" + beaconSection.getBoolean("enabled", true));
            sender.sendMessage("§e灾厄效果持续时间: §f" + beaconSection.getInt("doom-effect-duration", 300) + "秒");
            
            org.bukkit.configuration.ConfigurationSection detectionSection = beaconSection.getConfigurationSection("detection");
            if (detectionSection != null) {
                sender.sendMessage("§e需要容器底座: §f" + detectionSection.getBoolean("require-container", true));
            }
        }
        
        // 袭击配置
        sender.sendMessage("§6--- 袭击配置 ---");
        org.bukkit.configuration.ConfigurationSection raidSection = config.getConfigurationSection("raid");
        if (raidSection != null) {
            sender.sendMessage("§e袭击启用: §f" + raidSection.getBoolean("enabled", true));
            sender.sendMessage("§e袭击冷却: §f" + raidSection.getLong("cooldown", 3600) + "秒");
        }
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 显示性能统计信息
     */
    private void showPerformanceStats(CommandSender sender) {
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6[YinwuRaid] §e性能统计");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        
        if (plugin.getSpecialRaidListener() != null) {
            // 这里可以添加更多性能统计信息
            sender.sendMessage("§e袭击系统: §a正常运行");
        }
        
        sender.sendMessage("§e内存使用: §f" + getMemoryUsage());
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 显示信标状态信息
     */
    private void showBeaconInfo(CommandSender sender) {
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6[YinwuRaid] §e信标状态");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        
        if (plugin.getBeaconDetector() != null) {
            org.bukkit.Location lastBeacon = plugin.getBeaconDetector().getLastDetectedBeacon();
            if (lastBeacon != null) {
                sender.sendMessage("§e信标位置: §f" + formatLocation(lastBeacon));
                
                int beaconLevel = plugin.getBeaconDetector().getBeaconLevel(lastBeacon);
                sender.sendMessage("§e信标等级: §f" + beaconLevel + (org.yinwu.enums.BeaconLevel.fromInt(beaconLevel).isEasterEgg() ? " (§d彩蛋级§f)" : ""));
                
                boolean hasContainer = plugin.getBeaconDetector().hasContainerBelowBeacon(lastBeacon);
                sender.sendMessage("§e容器底座: §f" + (hasContainer ? "§a存在" : "§c缺失"));
            } else {
                sender.sendMessage("§e信标状态: §c未检测到信标");
                sender.sendMessage("§7请搭建灾厄信标结构后右键信标");
            }
        }
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 处理 debug spawn 命令 - 生成测试怪物
     */
    private void handleDebugSpawn(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }
        
        if (args.length < 3) {
            sender.sendMessage("§c用法：/yinwuraid debug spawn <mobType> [count]");
            sender.sendMessage("§7示例：/yinwuraid debug spawn ZOMBIE 5");
            return;
        }
        
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        String mobTypeName = args[2].toUpperCase();
        int countParam = 1;
        
        if (args.length >= 4) {
            try {
                countParam = Integer.parseInt(args[3]);
                if (countParam <= 0 || countParam > 100) {
                    sender.sendMessage("§c数量必须在 1-100 之间！");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c无效的数量！");
                return;
            }
        }
        
        org.bukkit.entity.EntityType entityType;
        try {
            entityType = org.bukkit.entity.EntityType.valueOf(mobTypeName);
        } catch (IllegalArgumentException e) {
            sender.sendMessage("§c无效的怪物类型：" + mobTypeName);
            return;
        }
        
        org.bukkit.Location location = player.getLocation();
        final int count = countParam; // ✅ 声明为 final 以在 lambda 中使用
        
        // ✅ Folia 兼容：在玩家所在区域线程中生成实体
        Bukkit.getRegionScheduler().run(plugin, location, (task) -> {
            if (!player.isOnline()) return;
            
            int spawnedCount = 0;
            for (int i = 0; i < count; i++) {
                org.bukkit.entity.Entity entity = location.getWorld().spawnEntity(location, entityType);
                if (entity != null) {
                    spawnedCount++;
                }
            }
            
            final int finalCount = spawnedCount;
            Bukkit.getRegionScheduler().run(plugin, location, (msgTask) -> {
                if (!player.isOnline()) return;
                player.sendMessage("§a━━━━━━━━━━━━━━━━");
                player.sendMessage("§6[YinwuRaid] §e已生成测试怪物");
                player.sendMessage("§e类型: §f" + mobTypeName);
                player.sendMessage("§e数量: §f" + finalCount);
                player.sendMessage("§e位置: §f" + formatLocation(location));
                player.sendMessage("§a━━━━━━━━━━━━━━━━");
            });
        });
    }
    
    /**
     * ✅ 处理 debug trigger 命令 - 手动触发袭击
     */
    private void handleDebugTrigger(CommandSender sender, String[] args) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            sender.sendMessage("§c此命令只能由玩家执行！");
            return;
        }
        
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) sender;
        int doomLevel = 7; // 默认等级
        
        if (args.length >= 3) {
            try {
                doomLevel = Integer.parseInt(args[2]);
                if (doomLevel < 7 || doomLevel > 10) {
                    sender.sendMessage("§c灾厄等级必须在 7-10 之间！");
                    return;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage("§c无效的等级！");
                return;
            }
        }
        
        // 给玩家施加灾厄效果以触发袭击
        org.bukkit.potion.PotionEffect effect = new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.RAID_OMEN,
            6000, // 5分钟
            doomLevel - 1, // Bukkit 等级从 0 开始
            false,
            true,
            true
        );
        
        player.addPotionEffect(effect);
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6[YinwuRaid] §e已手动触发灾厄袭击");
        sender.sendMessage("§e灾厄等级: §f" + doomLevel);
        sender.sendMessage("§7请进入村庄以触发袭击...");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 处理 debug reloadbeacon 命令 - 刷新信标状态
     */
    private void handleDebugReloadBeacon(CommandSender sender) {
        if (plugin.getBeaconDetector() == null) {
            sender.sendMessage("§c信标检测器未初始化！");
            return;
        }
        
        // 重新加载信标配置
        plugin.getConfigManager().reload();
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6[YinwuRaid] §e信标状态已刷新");
        sender.sendMessage("§e最后检测信标: §f" + 
            (plugin.getBeaconDetector().getLastDetectedBeacon() != null ? 
             formatLocation(plugin.getBeaconDetector().getLastDetectedBeacon()) : "未检测到"));
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    /**
     * ✅ 检查是否为 Folia 服务器
     */
    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * ✅ 格式化位置信息
     */
    private String formatLocation(org.bukkit.Location location) {
        return String.format("%s (%.0f, %.0f, %.0f)", 
            location.getWorld() != null ? location.getWorld().getName() : "未知世界",
            location.getX(), location.getY(), location.getZ());
    }
    
    /**
     * ✅ 获取内存使用情况
     */
    private String getMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMB = runtime.maxMemory() / 1024 / 1024;
        return usedMB + "MB / " + maxMB + "MB";
    }
    
    /**
     * 发送帮助信息
     */
    private void sendHelpMessage(CommandSender sender) {
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§6§l[YinwuRaid] §e灾厄袭击插件 v" + plugin.getDescription().getVersion());
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§e/yinwuraid §7- 显示此帮助信息");
        
        if (sender.hasPermission("yinwuraid.admin.reload")) {
            sender.sendMessage("§e/yinwuraid reload §7- 重新加载配置文件");
        }
        
        if (sender.hasPermission("yinwuraid.admin.giveitem")) {
            sender.sendMessage("§e/yinwuraid giveitem <物品> [数量] §7- 给予测试物品");
        }
        
        if (sender.hasPermission("yinwuraid.admin.debug")) {
            sender.sendMessage("§e/yinwuraid debug §7- 显示调试信息");
        }
        
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
        sender.sendMessage("§7作者：Qumingjam");
        sender.sendMessage("§7网站：server.yinwurealm.org");
        sender.sendMessage("§a━━━━━━━━━━━━━━━━");
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // 子命令补全
            List<String> subCommands = new ArrayList<>();
            subCommands.add("reload");
            subCommands.add("giveitem");
            subCommands.add("debug");
            
            for (String subCommand : subCommands) {
                if (subCommand.startsWith(args[0].toLowerCase())) {
                    // 检查权限
                    if (subCommand.equals("reload") && !sender.hasPermission("yinwuraid.admin.reload")) {
                        continue;
                    }
                    if (subCommand.equals("giveitem") && !sender.hasPermission("yinwuraid.admin.giveitem")) {
                        continue;
                    }
                    if (subCommand.equals("debug") && !sender.hasPermission("yinwuraid.admin.debug")) {
                        continue;
                    }
                    completions.add(subCommand);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("giveitem")) {
            // 物品名称补全
            if (sender.hasPermission("yinwuraid.admin.giveitem")) {
                List<String> items = Arrays.asList(
                    "SEED1", "SEED2",
                    "DISASTER_SEED_1", "DISASTER_SEED_2"
                );
                
                for (String item : items) {
                    if (item.startsWith(args[1].toUpperCase())) {
                        completions.add(item);
                    }
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("giveitem")) {
            // 数量补全
            if (sender.hasPermission("yinwuraid.admin.giveitem")) {
                completions.addAll(Arrays.asList("1", "5", "10", "16", "32", "64"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            // debug 子命令补全
            if (sender.hasPermission("yinwuraid.admin.debug")) {
                List<String> debugCommands = Arrays.asList("info", "config", "stats", "beacon", "spawn", "trigger", "reloadbeacon");
                
                for (String debugCmd : debugCommands) {
                    if (debugCmd.startsWith(args[1].toLowerCase())) {
                        completions.add(debugCmd);
                    }
                }
            }
        }
        
        return completions;
    }
}