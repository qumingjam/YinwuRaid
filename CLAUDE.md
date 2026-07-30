# YinwuRaid — 灾厄袭击插件

## 项目信息
- **技术栈**: Java 21, Maven, Paper API 1.21.4
- **打包**: `mvn clean package` → `target/YinwuRaid-<version>.jar`
- **Folia 兼容**: 是
- **GitHub**: https://github.com/qumingjam/YinwuRaid

## 功能
- 倒置信标（搭建结构触发灾厄效果）
- 灾厄之种（可升级成书，多阶段强化）
- 特殊袭击（多波次、精英怪与 Boss）
- 村民奖励（职业绑定奖励池）
- 灾厄效果（粒子、迷雾、BossBar）

## 共享规则
继承自 `YinwuForge/agents.md`（适用于所有 Yinwu 插件）：

### 调度规范（Folia）
- ✅ 使用 `RegionScheduler` / `GlobalRegionScheduler` / `EntityScheduler`
- ❌ 禁止 `Bukkit.getScheduler()`、`runTask`、`runTaskAsynchronously`
- ❌ 初始延迟禁止为 `0L`（必须 ≥ `1L`）

### 代码风格
- 注释极简，无废话
- 仅使用 Paper / Folia API，禁止 NMS
