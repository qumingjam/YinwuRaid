# YinwuRaid — 灾厄袭击

**最新版本：v1.2.0** | [下载 Release](https://github.com/qumingjam/YinwuRaid/releases/tag/v1.2.0)

一个为 **Folia / Paper 1.21+** 设计的倒置信标与特殊袭击插件。

> ⚡ 完全兼容 Folia 区域线程调度，无 NMS、无 unsafe 反射。

---

## 功能概览

| 模块 | 说明 |
|------|------|
| 🗼 **倒置信标** | 玩家搭建倒置信标结构激活，触发灾厄效果与特殊袭击 |
| 🌱 **灾厄之种** | 可升级的成书物品，提供多阶段强化系统 |
| ⚔️ **特殊袭击** | 多波次、多等级、包含精英怪与Boss的袭击系统 |
| 🎁 **村民奖励** | 村民职业绑定奖励池，自定义战利品配置 |
| 🎯 **灾厄效果** | 视觉粒子、迷雾效果、BossBar 状态追踪 |

---

## 快速开始

1. 将 `YinwuRaid.jar` 放入 `plugins/` 目录
2. 重启服务器
3. 搭建**倒置信标**结构（信标方块倒置放置）
4. 右键信标激活 → 获得**灾厄之种**
5. 右键灾厄之种触发**灾厄效果**
6. 携带灾厄效果进入村庄 → 触发**特殊袭击**

---

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/yinwuraid` | 查看帮助 | 无 |
| `/yinwuraid reload` | 重新加载配置 | `yinwuraid.admin.reload` |
| `/yinwuraid giveitem <物品> [数量]` | 给予测试物品 | `yinwuraid.admin.giveitem` |
| `/yinwuraid debug info` | 插件信息 | `yinwuraid.admin.debug` |
| `/yinwuraid debug config` | 配置信息 | `yinwuraid.admin.debug` |
| `/yinwuraid debug stats` | 性能统计 | `yinwuraid.admin.debug` |
| `/yinwuraid debug beacon` | 信标状态 | `yinwuraid.admin.debug` |
| `/yinwuraid debug spawn <类型> [数量]` | 生成测试怪物 | `yinwuraid.admin.debug` |
| `/yinwuraid debug trigger [等级]` | 手动触发袭击 | `yinwuraid.admin.debug` |
| `/yinwuraid debug reloadbeacon` | 刷新信标状态 | `yinwuraid.admin.debug` |

别名：`/yr`

---

## 权限

| 权限节点 | 说明 | 默认 |
|----------|------|------|
| `yinwuraid.admin.*` | 所有管理员权限 | OP |
| `yinwuraid.admin.reload` | 重新加载配置 | OP |
| `yinwuraid.admin.giveitem` | 给予测试物品 | OP |
| `yinwuraid.admin.debug` | 调试命令 | OP |
| `yinwuraid.beacon.use` | 允许使用倒置信标 | 所有人 |
| `yinwuraid.raid.participate` | 参与特殊袭击 | 所有人 |

---

## 架构

```
YinwuRaid
├── beacon/                    # 倒置信标系统
│   ├── BeaconInteractionListener  # 信标交互事件处理
│   ├── InvertedBeaconDetector     # 倒置信标结构检测
│   ├── DisasterSeedManager        # 灾厄之种管理
│   └── EnchantmentRuleManager     # 附魔规则
├── raid/                      # 袭击系统
│   ├── SpecialRaidListener        # 袭击主监听器
│   ├── RaidMobManager             # 怪物生成与管理
│   ├── RaidBossBarManager         # BossBar 管理
│   ├── RaidDefenderManager        # 防御者 AI
│   ├── RaidFogEffectManager       # 迷雾粒子效果
│   ├── RaidLootManager            # 战利品管理
│   └── RaidState                  # 袭击状态
├── reward/                    # 奖励系统
│   ├── VillagerRewardManager      # 村民奖励
│   ├── GiftThrowManager           # 赠礼检测
│   └── RewardEntry                # 奖励条目
├── effect/                    # 效果系统
│   └── DoomEffectManager          # 灾厄效果管理
├── config/                    # 配置系统
│   └── ConfigManager              # 统一配置管理
├── command/                   # 命令系统
│   └── YinwuRaidCommand           # 命令处理器
├── util/                      # 工具类
│   ├── PluginLogger               # 日志工具
│   ├── ThreadSafetyUtils          # 线程安全检查
│   ├── MythicMobsIntegration      # MythicMobs 集成
│   └── ConfigUtils                # 配置工具
└── enums/
    └── BeaconLevel                # 信标等级枚举
```

---

## 构建

依赖：**Java 21+**、**Maven 3.8+**

```bash
git clone https://github.com/qumingjam/YinwuRaid.git
cd YinwuRaid
mvn clean package
```

产出：`target/YinwuRaid-1.2.0.jar`

---

## 依赖

- **[Paper API 1.21.11](https://papermc.io/)**（provided，必选）
- **MythicMobs**（可选，启用后可使用自定义怪物）

---

## 配置

配置文件位于 `plugins/YinwuRaid/`：

```
config.yml              # 主配置文件
raid/config.yml         # 袭击配置
rewards/config.yml      # 奖励配置
```

使用 `/yinwuraid reload` 热重载所有配置。

---

## 设计原则

- **Folia First** — 所有调度使用 `RegionScheduler` / `GlobalRegionScheduler` / `EntityScheduler`
- **零 NMS** — 仅依赖 Paper/Folia 公共 API，不碰 Mojang 内部类
- **线程安全** — 共享状态使用 `ConcurrentHashMap`，实体/方块操作在对应区域线程执行
- **Java 21** — 使用最新语言特性（records、switch expressions、pattern matching）

---

## 链接

- 仓库：[github.com/qumingjam/YinwuRaid](https://github.com/qumingjam/YinwuRaid)
- 作者：Qumingjam
- 网站：server.yinwurealm.org

---

## 📋 更新日志 (v1.2.0)

- 波次进度从轮询改为事件驱动：`RaidState.aliveMobs`（`AtomicInteger`）+ `onEntityDeath` 即时触发下一波
- 新增 `PlayerQuitEvent` 处理器：玩家退出时清理 BossBar 和 `RaidState`
- `RaidMobManager` 6 处空 `catch` 块添加 `fine` 级别日志
- `ThreadSafetyUtils` 移除 `Class.forName` 运行时 Folia 探测，直接使用 `Bukkit.isOwnedByCurrentRegion()`
- 修复 `createRaidBossBar` 中 RaidState 构造器参数不匹配（漏传 `playerId`）
- `NamespacedKey.minecraft()` 全部替换为 `NamespacedKey.fromString()`（Paper 1.21+ 废弃 API）
- `RewardEntry.java` 移除 3 处陈旧的 `"deprecation"` `@SuppressWarnings`
- `onDisable()` 新增 `HandlerList.unregisterAll(this)`
