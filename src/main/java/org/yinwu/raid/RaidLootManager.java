package org.yinwu.raid;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.ConfigManager;
import org.yinwu.config.ConfigManager.LevelLootEntry;

import java.util.*;

/**
 * 灾厄袭击战利品管理器
 * 负责战利品生成和容器填充
 */
public class RaidLootManager {

    private final YinwuRaidPlugin plugin;
    private final ConfigManager configManager;
    private final SpecialRaidListener listener;

    // 战利品配置缓存
    private int emeraldBaseAmount = 10;
    private int expBottleMultiplier = 2;
    private double enchantedGoldenAppleChance = 0.3;
    private Map<Integer, List<LevelLootEntry>> perLevelLoot = new HashMap<>();

    public RaidLootManager(YinwuRaidPlugin plugin, ConfigManager configManager, SpecialRaidListener listener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.listener = listener;

        // 加载战利品配置
        loadLootConfig();
    }

    /**
     * 加载战利品配置
     */
    private void loadLootConfig() {
        ConfigManager.LootConfig lootConfig = configManager.getLootConfig();
        if (lootConfig != null) {
            emeraldBaseAmount = lootConfig.getEmeraldBaseAmount();
            expBottleMultiplier = lootConfig.getExpBottleMultiplier();
            enchantedGoldenAppleChance = lootConfig.getEnchantedGoldenAppleChance();
            perLevelLoot = lootConfig.getPerLevelLoot();
            if (perLevelLoot == null) {
                perLevelLoot = new HashMap<>();
            }
        }
    }

    public void reloadLootConfig() {
        loadLootConfig();
    }

    public int getEmeraldBaseAmount() { return emeraldBaseAmount; }
    public int getExpBottleMultiplier() { return expBottleMultiplier; }
    public double getEnchantedGoldenAppleChance() { return enchantedGoldenAppleChance; }

    // ============== 战利品发放 ==============

    /**
     * 给予灾厄袭击战利品
     */
    public void giveRaidLoot(Player player, int doomLevel) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidLootManager] giveRaidLoot: 玩家=" + player.getName() + ", 灾厄等级=" + doomLevel);
        }
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, (task) -> {
            if (!player.isOnline()) return;

            Location beaconLocation = listener.getBeaconLocation(player.getWorld().getName());

            if (beaconLocation == null) {
                listener.sendRaidActionBar(player, "§e\u26A0 未找到信标容器，使用备用方案...");
                giveLootViaVirtualChest(player, doomLevel);
                return;
            }

            ConfigManager.BeaconConfig beaconConfig = configManager.getBeaconConfig();
            int range = beaconConfig != null ? beaconConfig.getMaxRange() : 50;
            int playerCount = 1;

            for (Player nearbyPlayer : beaconLocation.getWorld().getPlayers()) {
                if (!nearbyPlayer.equals(player) &&
                    nearbyPlayer.getLocation().distance(beaconLocation) <= range) {
                    playerCount++;
                }
            }

            final int finalPlayerCount = playerCount;

            Bukkit.getRegionScheduler().run(plugin, beaconLocation, (regionTask) -> {
                fillContainer(beaconLocation, doomLevel, player, finalPlayerCount);
            });
        }, 20L);
    }

    /**
     * 填充信标正下方的容器
     */
    private void fillContainer(Location beaconLocation, int doomLevel, Player player, int playerCount) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidLootManager] fillContainer: 信标位置=(" + beaconLocation.getBlockX() + "," + beaconLocation.getBlockY() + "," + beaconLocation.getBlockZ() + "), 灾厄等级=" + doomLevel + ", 玩家=" + player.getName() + ", 玩家数量=" + playerCount);
        }

        Location containerLocation = beaconLocation.clone().subtract(0, 1, 0);
        org.bukkit.block.Block containerBlock = containerLocation.getBlock();

        Material containerType = containerBlock.getType();
        boolean isBarrel = containerType == Material.BARREL;
        boolean isChest = containerType == Material.CHEST;

        if (!isBarrel && !isChest) {
            plugin.getLogger().warning(String.format("§c\u26A0 位置 %s 不是木桶或木箱！类型：%s",
                containerLocation.toString(), containerType.name()));

            listener.sendRaidActionBar(player, "§e\u26A0 信标正下方必须放置一个木桶或木箱才能领取战利品！");
            listener.sendRaidActionBar(player, "§e 请在信标正下方放置木桶或木箱，然后重新触发灾厄袭击。");

            Bukkit.getGlobalRegionScheduler().run(plugin, (t) -> {
                giveLootViaVirtualChest(player, doomLevel);
            });
            return;
        }

        String containerName = isBarrel ? "木桶" : "木箱";
        listener.sendRaidActionBar(player, String.format("§a\u2713 战利品已放入信标下方的%s中！", containerName));

        org.bukkit.block.BlockState blockState = containerBlock.getState();
        if (!(blockState instanceof InventoryHolder)) {
            plugin.getLogger().warning(String.format("§c\u26A0 %s 不是 InventoryHolder！使用备用方案...", containerName));
            Bukkit.getGlobalRegionScheduler().run(plugin, (t) -> {
                giveLootViaVirtualChest(player, doomLevel);
            });
            return;
        }

        InventoryHolder holder = (InventoryHolder) blockState;
        Inventory containerInv = holder.getInventory();

        List<ItemStack> overflowLoot = fillLootChestWithMultiplier(containerInv, doomLevel, playerCount);

        if (!overflowLoot.isEmpty()) {
            listener.sendRaidActionBar(player,
                String.format("§e\u26A0 %s 空间不足，部分战利品将以掉落物形式掉落在容器下方！", containerName));

            Location dropLocation = containerLocation.clone().add(0.5, -1, 0.5);
            for (ItemStack item : overflowLoot) {
                containerLocation.getWorld().dropItemNaturally(dropLocation, item);
            }

            containerLocation.getWorld().playSound(dropLocation, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.0f);
        }

        String message = String.format("§a\u2713 已将 %d 级战利品放入%s中！", doomLevel, containerName);
        listener.sendRaidActionBar(player, message);

        containerLocation.getWorld().playSound(containerLocation, Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f);
        containerLocation.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
            containerLocation.clone().add(0.5, 1, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
    }

    /**
     * 通过虚拟箱子给予战利品（备用方案）
     */
    private void giveLootViaVirtualChest(Player player, int doomLevel) {
        Inventory lootChest = Bukkit.createInventory(null, 27, "§4§l灾厄袭击战利品");
        fillLootChest(lootChest, doomLevel);
        player.openInventory(lootChest);
        listener.sendRaidActionBar(player, "§a\u2713 你获得了灾厄袭击战利品箱（虚拟）！");
        plugin.getLogger().info(String.format("§a\u2713 已给予玩家 %s 灾厄等级 %d 的战利品（虚拟箱子）",
            player.getName(), doomLevel));
    }

    /**
     * 根据 LevelLootEntry 创建物品
     */
    private ItemStack createItemFromEntry(LevelLootEntry entry) {
        // 灾厄之种（WRITTEN_BOOK + seed-type）
        if (entry.getSeedType() > 0 && "WRITTEN_BOOK".equalsIgnoreCase(entry.getMaterial())) {
            return createDisasterSeed(entry.getSeedType(), entry.getAmount());
        }

        // 附魔书（ENCHANTED_BOOK + enchant）
        if ("ENCHANTED_BOOK".equalsIgnoreCase(entry.getMaterial()) && entry.getEnchant() != null && !entry.getEnchant().isEmpty()) {
            Enchantment enchantment = Enchantment.getByKey(
                org.bukkit.NamespacedKey.minecraft(entry.getEnchant().toLowerCase())
            );
            if (enchantment != null) {
                int level = entry.getEnchantLevel() > 0 ? entry.getEnchantLevel() : 1;
                return createEnchantedBook(enchantment, level, entry.getAmount());
            }
            plugin.getLogger().warning("未知附魔: " + entry.getEnchant() + "，使用普通附魔书");
            return new ItemStack(Material.ENCHANTED_BOOK, entry.getAmount());
        }

        // 普通物品
        try {
            Material material = Material.valueOf(entry.getMaterial().toUpperCase());
            return new ItemStack(material, entry.getAmount());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的物品材料: " + entry.getMaterial());
            return new ItemStack(Material.AIR);
        }
    }

    /**
     * 填充战利品箱子（单次）
     */
    private void fillLootChest(Inventory chest, int doomLevel) {
        List<ItemStack> loot = new ArrayList<>();

        if (doomLevel != 6) {
            loot.add(new ItemStack(Material.EMERALD, emeraldBaseAmount + doomLevel));
            loot.add(new ItemStack(Material.EXPERIENCE_BOTTLE, doomLevel * expBottleMultiplier));
        }

        // ✅ 从配置读取该难度的专属战利品
        List<LevelLootEntry> levelEntries = perLevelLoot.get(doomLevel);
        if (levelEntries != null) {
            for (LevelLootEntry entry : levelEntries) {
                ItemStack item = createItemFromEntry(entry);
                if (item.getType() != Material.AIR) {
                    loot.add(item);
                }
            }
        }

        Random random = new Random();
        if (random.nextDouble() < enchantedGoldenAppleChance) {
            loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        }

        int itemsAdded = Math.min(loot.size(), chest.getSize());
        for (int i = 0; i < itemsAdded; i++) {
            chest.setItem(i, loot.get(i));
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidLootManager] fillLootChest: 灾厄等级=" + doomLevel + ", 总战利品种类=" + loot.size() + ", 实际添加=" + itemsAdded);
        }
    }

    /**
     * 填充战利品箱子（带倍数，保留原有物品）
     */
    private List<ItemStack> fillLootChestWithMultiplier(Inventory chest, int doomLevel, int playerCount) {
        List<ItemStack> overflowLoot = new ArrayList<>();
        List<ItemStack> loot = new ArrayList<>();

        for (int i = 0; i < playerCount; i++) {
            if (doomLevel != 6) {
                loot.add(new ItemStack(Material.EMERALD, emeraldBaseAmount + doomLevel));
                loot.add(new ItemStack(Material.EXPERIENCE_BOTTLE, doomLevel * expBottleMultiplier));
            }

            // ✅ 从配置读取该难度的专属战利品（每人一份）
            List<LevelLootEntry> levelEntries = perLevelLoot.get(doomLevel);
            if (levelEntries != null) {
                for (LevelLootEntry entry : levelEntries) {
                    ItemStack item = createItemFromEntry(entry);
                    if (item.getType() != Material.AIR) {
                        loot.add(item);
                    }
                }
            }
        }

        // ✅ 灾厄之种（seed-type）已包含在 per-level 配置中，不再需要单独的 switch
        // 注：在多人模式下灾厄之种会按人数倍数发放，符合预期

        Random random = new Random();
        if (random.nextDouble() < enchantedGoldenAppleChance) {
            loot.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
        }

        int totalItems = loot.size();
        for (ItemStack item : loot) {
            HashMap<Integer, ItemStack> remaining = chest.addItem(item);
            if (!remaining.isEmpty()) {
                for (ItemStack overflow : remaining.values()) {
                    overflowLoot.add(overflow);
                }
            }
        }

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidLootManager] fillLootChestWithMultiplier: 灾厄等级=" + doomLevel + ", 玩家数=" + playerCount + ", 总物品数=" + totalItems + ", 溢出物品数=" + overflowLoot.size());
        }

        return overflowLoot;
    }

    /**
     * 创建附魔书
     */
    private ItemStack createEnchantedBook(Enchantment enchantment, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        book.addUnsafeEnchantment(enchantment, level);
        return book;
    }

    /**
     * 创建附魔书（带数量）
     */
    private ItemStack createEnchantedBook(Enchantment enchantment, int level, int amount) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, amount);
        book.addUnsafeEnchantment(enchantment, level);
        return book;
    }

    /**
     * 创建灾厄之种
     */
    private ItemStack createDisasterSeed(int type, int amount) {
        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [RaidLootManager] createDisasterSeed: 类型=" + type + ", 数量=" + amount + ", seedKey=" + (type == 1 ? "seed-1" : "seed-2"));
        }

        ItemStack seed = new ItemStack(Material.WRITTEN_BOOK, amount);
        org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) seed.getItemMeta();

        if (meta != null) {
            String seedKey = (type == 1) ? "seed-1" : "seed-2";

            // 设置显示名称
            meta.setDisplayName(type == 1 ? "§d灾厄之种I" : "§d灾厄之种II");

            // 从配置加载成书内容
            ConfigManager.EnhancementConfig enhancementConfig = configManager.getEnhancementConfig();
            if (enhancementConfig != null) {
                Map<String, ConfigManager.SeedBookConfig> seedBooks = enhancementConfig.getSeedBooks();
                if (seedBooks != null && seedBooks.containsKey(seedKey)) {
                    ConfigManager.SeedBookConfig bookConfig = seedBooks.get(seedKey);
                    if (bookConfig.getTitle() != null && !bookConfig.getTitle().isEmpty()) {
                        meta.setTitle(bookConfig.getTitle());
                    }
                    if (bookConfig.getAuthor() != null && !bookConfig.getAuthor().isEmpty()) {
                        meta.setAuthor(bookConfig.getAuthor());
                    }
                    if (bookConfig.getPages() != null && !bookConfig.getPages().isEmpty()) {
                        meta.setPages(bookConfig.getPages());
                    }
                }
            }

            seed.setItemMeta(meta);
        }

        return seed;
    }

    /**
     * 创建灾厄之种（单个）
     */
    private ItemStack createDisasterSeed(int type) {
        return createDisasterSeed(type, 1);
    }
}
