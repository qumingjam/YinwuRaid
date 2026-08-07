package org.yinwu.beacon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yinwu.YinwuRaidPlugin;
import org.yinwu.config.BeaconConfig;
import org.yinwu.config.ConfigManager;
import org.yinwu.effect.DoomEffectManager;
import org.yinwu.enums.BeaconLevel;
import org.yinwu.gui.BeaconGUI;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 灾厄信标交互监听器
 * 处理玩家与灾厄信标的交互
 */
public class BeaconInteractionListener implements Listener {

    private final YinwuRaidPlugin plugin;
    private final InvertedBeaconDetector detector;
    private final DoomEffectManager effectManager;
    private final DisasterSeedManager seedManager;
    private final ConfigManager configManager;
    private final BeaconGUI beaconGUI;
    private final BeaconEnhancer enhancer;

    // 彩蛋级信标等级
    private static final int EASTER_EGG_LEVEL = 6;
    // 激活材料槽位（由 BeaconGUI 创建，listener 通过 PlayerOpenInventory 读取）
    private static final int STAR_SLOT = 44;

    // GUI 标题（点击路由识别用）
    private static final String BEACON_GUI_TITLE = "§4§l灾厄信标";
    private static final String ENHANCE_GUI_TITLE = "§6§l灾厄强化";

    // 每个玩家打开信标GUI时的信标等级 (UUID -> beaconLevel)
    private final Map<UUID, Integer> playerBeaconLevels = new ConcurrentHashMap<>();

    public BeaconInteractionListener(YinwuRaidPlugin plugin, InvertedBeaconDetector detector, DoomEffectManager effectManager) {
        this.plugin = plugin;
        this.detector = detector;
        this.effectManager = effectManager;
        this.seedManager = new DisasterSeedManager(plugin);
        this.configManager = plugin.getConfigManager();
        this.beaconGUI = new BeaconGUI(plugin, this, configManager, seedManager);
        this.enhancer = new BeaconEnhancer(plugin, configManager, seedManager);
    }

    /** 注入 EnchantAPI（YinwuEnchant 联动） */
    public void setEnchantAPI(net.yinwu.lib.api.EnchantAPI enchantAPI) {
        this.seedManager.setEnchantAPI(enchantAPI);
    }

    /** 玩家退出时清理信标等级缓存（防无界增长） */
    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        playerBeaconLevels.remove(event.getPlayer().getUniqueId());
    }

    // ==================== 事件：玩家交互 ====================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.BEACON) return;

        Player player = event.getPlayer();
        Location beaconLocation = block.getLocation();

        Bukkit.getRegionScheduler().run(plugin, beaconLocation, (task) -> {
            handleBeaconInteraction(player, beaconLocation);
        });
    }

    private void handleBeaconInteraction(Player player, Location beaconLocation) {
        BeaconConfig beaconConfig = configManager.getBeaconConfig();

        if (!detector.isInvertedBeacon(beaconLocation)) return;

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 玩家 " + player.getName() + " 右键点击信标，位置：" + beaconLocation);
        }

        if (ThreadLocalRandom.current().nextDouble() < 0.1) {
            plugin.getLogger().fine("信标交互：" + player.getName());
        }

        plugin.getSpecialRaidListener().setBeaconLocation(player, beaconLocation);

        if (beaconConfig != null && !beaconConfig.isEnabled()) {
            sendActionBar(player, "§c[系统] §7灾厄信标功能已禁用");
            return;
        }

        if (!detector.hasContainerBelowBeacon(beaconLocation)) {
            sendActionBar(player, "§c§l❌ 灾厄信标结构不完整！");
            return;
        }

        boolean requireContainer = beaconConfig != null ? beaconConfig.isRequireContainer() : true;
        if (requireContainer) {
            Location belowLocation = beaconLocation.clone().subtract(0, 1, 0);
            Material belowMaterial = belowLocation.getBlock().getType();
            boolean isContainer = detector.getContainerTypes() != null &&
                                 detector.getContainerTypes().contains(belowMaterial);
            if (!isContainer) {
                plugin.getLogger().fine("检测到信标 B: " + beaconLocation);
                return;
            }
        }

        int beaconLevel = detector.getBeaconLevel(beaconLocation);

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 玩家 " + player.getName() + " 信标等级: " + beaconLevel + "，位置：" + beaconLocation);
        }

        playerBeaconLevels.put(player.getUniqueId(), beaconLevel);

        // Rule 6：openInventory / sendMessage 属玩家操作，派发到玩家线程（当前在信标区域线程）
        final int level = beaconLevel;
        player.getScheduler().run(plugin, (task) -> {
            if (!player.isOnline()) return;

            // 委托 BeaconGUI 打开界面（传入检测等级，按真实结构显示 ✓/✗）
            beaconGUI.openBeaconGUI(player, level);

            if (!BeaconLevel.fromInt(level).isValid()) {
                player.sendMessage("§7━━━━━━━━━━━━━━━━");
                player.sendMessage("§c信标结构不完整，无法激活！");
                player.sendMessage("");
                player.sendMessage("§6【激活方法】");
                player.sendMessage("§f1. §b在信标下方 §e放置木桶");

                if (beaconConfig != null && beaconConfig.getLayers() != null) {
                    java.util.List<Integer> keys = new java.util.ArrayList<>(beaconConfig.getLayers().keySet());
                    keys.sort((a, b) -> a - b);

                    int step = 2;
                    for (Integer lv : keys) {
                        org.yinwu.config.LayerConfig layerConfig = beaconConfig.getLayers().get(lv);
                        if (layerConfig != null) {
                            String materialName = layerConfig.getMaterial();
                            int size = layerConfig.getSize();
                            String chineseName = getMaterialChineseName(materialName);
                            player.sendMessage("§f" + step + ". §b在信标上方 §e放置 " + size + "×" + size + " " + chineseName + " §7（激活 " + lv + " 级信标）");
                            step++;
                        }
                    }
                } else {
                    player.sendMessage("§f2. §b在信标上方 §e放置 3×3 铁块 §7（激活 1 级信标）");
                }

                player.sendMessage("§f" + 3 + ". §e完成后右键信标即可激活");
                player.sendMessage("");
                player.sendMessage("§c未检测到完整的结构层");
                player.sendMessage("§7━━━━━━━━━━━━━━━━");
            }
        }, null);
    }

    // ==================== 结构检测 ====================

    /** 检查信标位置是否为完整的灾厄信标结构 */
    public boolean isValidAltarStructure(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return detector.isInvertedBeacon(loc) && detector.hasContainerBelowBeacon(loc);
    }

    /** 检查单个方块是否为预期材料 */
    private boolean checkCell(Block block, Material expected) {
        return block != null && block.getType() == expected;
    }

    // ==================== 激活逻辑 ====================

    /** BeaconGUI 调用的公开激活入口 */
    public void tryActivateBeacon(Player player, boolean isEasterEgg) {
        if (plugin.getSpecialRaidListener().hasActiveRaid(player)) {
            sendActionBar(player, "§c⚠ 灾厄袭击正在进行中！");
            return;
        }
        // 在信标区域线程重新验证结构（GUI 打开后玩家可能拆掉方块）
        Location beaconLoc = detector.getLastDetectedBeacon();
        if (beaconLoc == null || beaconLoc.getWorld() == null) {
            sendActionBar(player, "§c未检测到信标位置！");
            return;
        }
        final boolean easter = isEasterEgg;
        Bukkit.getRegionScheduler().run(plugin, beaconLoc, (task) -> {
            int currentLevel = detector.getBeaconLevel(beaconLoc);
            if (currentLevel <= 0) {
                sendActionBar(player, "§c信标结构不完整，无法激活！");
                return;
            }
            // 回玩家线程执行激活（GUI/材料操作必须在玩家线程）
            player.getScheduler().run(plugin, (pt) -> {
                if (!player.isOnline()) return;
                playerBeaconLevels.put(player.getUniqueId(), currentLevel);
                Inventory gui = player.getOpenInventory().getTopInventory();
                if (gui == null) return;
                ItemStack starItem = gui.getItem(STAR_SLOT);
                if (easter) {
                    handleEasterEggActivation(player, gui, starItem, currentLevel);
                } else {
                    handleNormalActivation(player, gui, starItem, currentLevel);
                }
            }, null);
        });
    }

    /** BeaconGUI 按钮打开的灾厄强化入口 */
    public void openEnhancer(Player player) {
        enhancer.openEnhancementGUI(player);
    }

    private void handleEasterEggActivation(Player player, Inventory gui, ItemStack starItem, int beaconLevel) {
        BeaconConfig eeBeaconConfig = configManager.getBeaconConfig();
        org.yinwu.config.ActivationConfig eeConfig = eeBeaconConfig != null ? eeBeaconConfig.getEasterEggActivationConfig() : null;

        if (configManager.isDebugEnabled()) {
            String materialName = (starItem != null) ? starItem.getType().name() : "null";
            int amount = (starItem != null) ? starItem.getAmount() : 0;
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 彩蛋激活 - 玩家: " + player.getName() + ", 材料: " + materialName + ", 数量: " + amount + ", 信标等级: " + beaconLevel);
        }
        if (eeConfig == null) {
            plugin.getLogger().warning("无效的彩蛋级激活材料配置");
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }

        String eeMaterialName = eeConfig.getMaterial();
        int eeAmount = eeConfig.getAmount();
        String eeDisplayName = eeConfig.getDisplayName();

        Material eeMaterial;
        try {
            eeMaterial = Material.valueOf(eeMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的彩蛋级激活材料配置：" + eeMaterialName);
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }

        if (starItem == null || starItem.getType() != eeMaterial) {
            sendActionBar(player, "§d⚠ 彩蛋信标需要 §d§l" + eeDisplayName + " §d才能激活！");
            return;
        }

        int bottleAmount = starItem.getAmount();

        if (bottleAmount > eeAmount) {
            ItemStack dropBottles = new ItemStack(eeMaterial, bottleAmount - eeAmount);
            Location dropLocation = player.getLocation().add(0, 1.5, 0);
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                if (!player.isOnline()) return;
                player.getWorld().dropItemNaturally(dropLocation, dropBottles);
            });
            sendActionBar(player, "§e⚠ " + (bottleAmount - eeAmount) + " 个" + eeDisplayName + "已返还");
        } else if (bottleAmount == eeAmount) {
            sendActionBar(player, "§d✨ 已消耗 " + eeAmount + " x " + eeDisplayName + " 激活彩蛋信标！");
        } else {
            sendActionBar(player, "§c⚠ 需要 " + eeAmount + " x " + eeDisplayName + " 才能激活！");
            return;
        }

        gui.setItem(STAR_SLOT, null);
        activateBeaconAndGiveEffect(player, beaconLevel);
    }

    private void handleNormalActivation(Player player, Inventory gui, ItemStack starItem, int beaconLevel) {
        BeaconConfig normalBeaconConfig = configManager.getBeaconConfig();
        org.yinwu.config.ActivationConfig activationConfig = normalBeaconConfig != null ? normalBeaconConfig.getActivationConfig() : null;

        if (configManager.isDebugEnabled()) {
            String materialName = (starItem != null) ? starItem.getType().name() : "null";
            int amount = (starItem != null) ? starItem.getAmount() : 0;
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 普通激活 - 玩家: " + player.getName() + ", 材料: " + materialName + ", 数量: " + amount + ", 信标等级: " + beaconLevel);
        }
        if (activationConfig == null) {
            plugin.getLogger().warning("无效的激活材料配置");
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }

        String activationMaterialName = activationConfig.getMaterial();
        int activationAmount = activationConfig.getAmount();
        String activationDisplayName = activationConfig.getDisplayName();

        Material activationMaterial;
        try {
            activationMaterial = Material.valueOf(activationMaterialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("无效的激活材料配置：" + activationMaterialName);
            sendActionBar(player, "§c⚠ 配置错误，请联系管理员");
            return;
        }

        if (starItem == null || starItem.getType() != activationMaterial) {
            if (configManager.isDebugEnabled()) {
                plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 普通激活失败 - 玩家: " + player.getName() + ", 缺少材料 " + activationDisplayName);
            }
            sendActionBar(player, "§c⚠ 没有" + activationDisplayName + "，无法激活！");
            return;
        }

        int starAmount = starItem.getAmount();

        if (configManager.isDebugEnabled()) {
            plugin.getLogger().info("§e[DEBUG] [BeaconInteractionListener] 普通激活成功 - 玩家: " + player.getName() + ", 材料: " + activationDisplayName + ", 数量: " + starAmount + ", 需要: " + activationAmount);
        }

        if (starAmount > activationAmount) {
            ItemStack dropStars = new ItemStack(activationMaterial, starAmount - activationAmount);
            Location dropLocation = player.getLocation().add(0, 1.5, 0);
            Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (task) -> {
                if (!player.isOnline()) return;
                player.getWorld().dropItemNaturally(dropLocation, dropStars);
            });
            sendActionBar(player, "§e⚠ " + (starAmount - activationAmount) + " 个" + activationDisplayName + "已掉落");
        } else if (starAmount == activationAmount) {
            sendActionBar(player, "§a✓ 已消耗 " + activationAmount + " x " + activationDisplayName + " 激活灾厄信标");
        } else {
            sendActionBar(player, "§c⚠ 需要 " + activationAmount + " x " + activationDisplayName + " 才能激活！");
            return;
        }

        gui.setItem(STAR_SLOT, null);
        activateBeaconAndGiveEffect(player, beaconLevel);
    }

    private void activateBeaconAndGiveEffect(Player player, int beaconLevel) {
        Location playerLoc = player.getLocation();
        Bukkit.getRegionScheduler().run(plugin, playerLoc, (task) -> {
            if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                plugin.getLogger().fine("激活信标等级：" + beaconLevel);
            }
            giveDoomEffect(player, beaconLevel);
        });

        Bukkit.getRegionScheduler().runDelayed(plugin, playerLoc, (task) -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        }, 1L);
    }

    private void giveDoomEffect(Player player, int beaconLevel) {
        BeaconConfig doomBeaconConfig = configManager.getBeaconConfig();

        java.util.Map<Integer, Integer> doomLevels = doomBeaconConfig != null ? doomBeaconConfig.getDoomLevels() : new java.util.HashMap<>();
        int doomLevel = doomLevels.getOrDefault(beaconLevel, beaconLevel + 6);
        int duration = (doomBeaconConfig != null ? doomBeaconConfig.getDoomEffectDuration() : 300) * 20;

        effectManager.applyDoomEffect(player, beaconLevel, duration);

        if (BeaconLevel.fromInt(beaconLevel).isEasterEgg()) {
            startEasterEggGreenEffect(player, duration);
        }

        Location beaconLocation = detector.getLastDetectedBeacon();
        int broadcastRadius = 100;

        sendActionBar(player, "§6[灾厄信标] §e你获得了 §4灾厄 §e效果！等级：" + doomLevel);

        if (beaconLocation != null) {
            broadcastActionBar(beaconLocation, broadcastRadius, "§6[灾厄信标] §e附近的玩家获得了 §4灾厄 §e效果！等级：" + doomLevel);
        }
    }

    private void startEasterEggGreenEffect(Player player, int duration) {
        UUID playerUuid = player.getUniqueId();

        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
            org.bukkit.potion.PotionEffectType.NIGHT_VISION,
            duration,
            0,
            false,
            false,
            true
        ));

        plugin.getLogger().fine(String.format("§d✨ [彩蛋视觉] 已为玩家 %s 启动绿色视觉效果！", player.getName()));

        Bukkit.getRegionScheduler().run(plugin, player.getLocation(), (regionTask) -> {
            try {
                Location eyeLoc = player.getEyeLocation();
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();

                int particleCount = 20;
                for (int i = 0; i < particleCount; i++) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double distance = 1.0 + random.nextDouble() * 2.0;

                    int x = (int) (eyeLoc.getBlockX() + Math.cos(angle) * distance);
                    int z = (int) (eyeLoc.getBlockZ() + Math.sin(angle) * distance);
                    int y = eyeLoc.getBlockY() + random.nextInt(3) - 1;

                    Location particleLoc = new Location(player.getWorld(), x + 0.5, y, z + 0.5);

                    player.getWorld().spawnParticle(
                        org.bukkit.Particle.DUST,
                        particleLoc,
                        1,
                        0.0f, 0.0f, 0.0f,
                        0.0f,
                        new org.bukkit.Particle.DustOptions(
                            org.bukkit.Color.fromRGB(0, 255, 0),
                            1.0f
                        )
                    );
                }
            } catch (Exception e) {
                plugin.getLogger().warning("§e⚠ 生成绿色粒子失败：" + e.getMessage());
            }
        });
    }

    // ==================== 事件：阻止铁砧修改灾厄强化物品 ====================

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack result = event.getResult();

        if (result != null && result.hasItemMeta()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "disaster_enhanced");
                byte isEnhanced = meta.getPersistentDataContainer().getOrDefault(
                    key,
                    org.bukkit.persistence.PersistentDataType.BYTE,
                    (byte) 0
                );

                if (isEnhanced == 1) {
                    event.setResult(new ItemStack(Material.AIR));
                    for (org.bukkit.entity.HumanEntity viewer : event.getViewers()) {
                        if (viewer instanceof Player) {
                            Player p = (Player) viewer;
                            p.sendMessage("§c❌ 该物品已被灾厄强化，无法通过铁砧修改！");
                        }
                    }
                }
            }
        }
    }

    // ==================== 事件：GUI 点击路由（断链#2 接线） ====================

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (top == null) return;
        String title = event.getView().getTitle();
        int rawSlot = event.getRawSlot();
        boolean topClicked = rawSlot >= 0 && rawSlot < top.getSize();

        // 灾厄信标 GUI
        if (BEACON_GUI_TITLE.equals(title)) {
            if (topClicked) {
                if (rawSlot == BeaconGUI.MATERIAL_SLOT) {
                    // 材料槽：只允许放下界之星 / 不详之瓶（1-5级），其余拒绝
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && !cursor.getType().isAir()
                            && cursor.getType() != Material.NETHER_STAR
                            && cursor.getType() != Material.OMINOUS_BOTTLE) {
                        event.setCancelled(true);
                        sendActionBar(player, "§c只能放入下界之星或不祥之瓶！");
                        return;
                    }
                    return; // 放行（放入/取出）
                }
                event.setCancelled(true);
                beaconGUI.handleClick(player, rawSlot, top);
                return;
            }
            // 从背包 shift-click 放入：唯一空槽是材料槽 44，校验物品类型
            if (event.isShiftClick()) {
                ItemStack current = event.getCurrentItem();
                if (current != null && !current.getType().isAir()
                        && current.getType() != Material.NETHER_STAR
                        && current.getType() != Material.OMINOUS_BOTTLE) {
                    event.setCancelled(true);
                    sendActionBar(player, "§c该信标 GUI 只能放入下界之星或不祥之瓶！");
                }
            }
            return;
        }

        // 灾厄强化 GUI
        if (ENHANCE_GUI_TITLE.equals(title)) {
            if (topClicked) {
                if (rawSlot == BeaconEnhancer.INPUT_TOOL_SLOT || rawSlot == BeaconEnhancer.INPUT_BOOK_SLOT) {
                    // 输入槽：允许放入/取出，点击后延迟刷新预览
                    enhancer.schedulePreviewUpdate(player);
                    return;
                }
                event.setCancelled(true);
                if (rawSlot == BeaconEnhancer.OUTPUT_SLOT) {
                    enhancer.handleOutputSlotClick(player, top);
                }
            } else {
                // 从背包 shift-click 放入输入槽时也刷新预览
                enhancer.schedulePreviewUpdate(player);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (BEACON_GUI_TITLE.equals(title)) {
            // 返还未消耗的激活材料（槽 44），防止关闭 GUI 后材料丢失
            ItemStack star = event.getView().getTopInventory().getItem(STAR_SLOT);
            if (star != null && star.getType() != Material.AIR) {
                var remaining = player.getInventory().addItem(star);
                for (ItemStack drop : remaining.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation().add(0, 1, 0), drop);
                }
            }
        } else if (ENHANCE_GUI_TITLE.equals(title)) {
            enhancer.handleGuiClose(player, event.getInventory());
        }
    }

    // ==================== 辅助方法 ====================

    private void sendActionBar(Player player, String message) {
        if (!player.isOnline()) return;

        // Rule 6：派发到玩家线程（避免跨线程读取 player.getLocation()）
        player.getScheduler().run(plugin, (task) -> {
            if (!player.isOnline()) return;
            try {
                player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(message));

                player.getScheduler().runDelayed(plugin, (clearTask) -> {
                    if (player.isOnline()) {
                        player.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(""));
                    }
                }, null, 60L);
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 发送 ActionBar 失败：" + e.getMessage());
            }
        }, null);
    }

    private void broadcastActionBar(Location center, double radius, String message) {
        if (center == null || center.getWorld() == null) return;

        Bukkit.getRegionScheduler().run(plugin, center, (task) -> {
            try {
                for (org.bukkit.entity.Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (entity instanceof Player) {
                        sendActionBar((Player) entity, message);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().fine("§e⚠ 广播 ActionBar 失败：" + e.getMessage());
            }
        });
    }

    /** 获取方块材料的中文名称 */
    private String getMaterialChineseName(String materialName) {
        switch (materialName.toUpperCase()) {
            case "NETHERITE_BLOCK": return "下界合金块";
            case "DIAMOND_BLOCK":   return "钻石块";
            case "GOLD_BLOCK":      return "金块";
            case "IRON_BLOCK":      return "铁块";
            case "BARREL":          return "木桶";
            case "CHEST":           return "箱子";
            case "TRAPPED_CHEST":   return "陷阱箱";
            case "SHULKER_BOX":     return "潜影盒";
            default:
                try {
                    Material material = Material.valueOf(materialName.toUpperCase());
                    return material.name().replace("_", "");
                } catch (IllegalArgumentException e) {
                    return materialName;
                }
        }
    }
}
