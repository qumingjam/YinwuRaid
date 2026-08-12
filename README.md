# YinwuRaid — Yinwu灾厄袭击
# YinwuRaid — Raid System

**最新版本：v1.2.3** | [下载 Release](https://github.com/qumingjam/YinwuRaid/releases/tag/v1.2.3)

Multi-wave calamity raid system triggered by high-level Bad Omen effects.

基于高等级不祥之兆触发的多波次灾厄袭击系统，包含信标、种子强化、村民奖励等完整机制。

> ⚡ 完全兼容 Folia 区域线程调度，全部事件驱动。

---

## Features | 功能概览

| 模块 | 说明 |
|------|------|
| 🗿 **倒置信标** | 自定义多层信标结构检测，触发灾厄效果 |
| 🌱 **灾厄之种** | 升级装备已有附魔等级，突破原版上限 |
| 👹 **多波次袭击** | 每波含幻术师，支持精英怪、Boss 机制 |
| 🎁 **村民奖励** | 职业绑定奖励池（15种），英雄等级越高越好 |
| 🌫️ **灾厄效果** | 粒子迷雾（白/绿）、BossBar、音效 |
| 🧟 **MythicMobs** | 软依赖，支持自定义生物生成 |

---

## Quick Start | 快速开始

1. 将 `YinwuRaid-1.2.3.jar` 放入 `plugins/` 目录
2. 重启服务器
3. 搭建**倒置信标**结构 → 激活灾厄效果
4. 获得不祥之兆 VI+ 效果 → 自动触发袭击

---

## Commands | 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/yinwuraid` | 查看帮助 | `yinwuraid.use` |
| `/yinwuraid start` | 手动触发袭击 | `yinwuraid.admin` |
| `/yinwuraid stop` | 停止当前袭击 | `yinwuraid.admin` |
| `/yinwuraid reload` | 重载配置 | `yinwuraid.admin` |

---

## Architecture | 架构

```
YinwuRaid
├── beacon/              # 信标系统
├── raid/                # 袭击核心（调度/刷怪/BossBar/Buff）
├── reward/              # 村民奖励
├── effect/              # 灾厄效果
├── gui/                 # GUI 界面
├── config/              # 26 个配置类
└── YinwuRaidPlugin      # 主类
```

---

## Build | 构建

```bash
git clone https://github.com/qumingjam/YinwuRaid.git
cd YinwuRaid
mvn clean package
```

产出：`target/YinwuRaid-1.2.3.jar`

---

## Dependencies | 依赖

- **[YinwuPluginLib](https://github.com/qumingjam/YinwuPluginLib)**（必需）
- **[Paper API 1.21+](https://papermc.io/)**（provided）
- **MythicMobs**（可选）

---

## Design Principles | 设计原则

- **事件驱动** — 无轮询，全部通过事件监听触发
- **Folia 安全** — 全部跨区域操作正确路由
- **配置化** — 26 个配置类，全部可热重载

---

## Links | 链接

- 仓库：[github.com/qumingjam/YinwuRaid](https://github.com/qumingjam/YinwuRaid)
- 关联：[YinwuForge](https://github.com/qumingjam/YinwuForge) | [YinwuEnchant](https://github.com/qumingjam/YinwuEnchant)
- 作者：Qumingjam
