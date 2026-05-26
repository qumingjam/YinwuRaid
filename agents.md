# Folia AI Agent 规则

## 输出规范

- 仅输出核心代码，注释极简
- 无铺垫、无废话、无重复推理
- 上下文仅保留最近 2–3 轮有效对话
- 同项目单对话，不新建会话
- 仅输出修改部分，不粘贴完整代码
- 输出长度严格精简，拒绝超长 token

---

## Folia 强制调度规范

### 允许的调度器

| 调度器 | 用途 |
|--------|------|
| `RegionizedTask` | 区域化任务 |
| `RegionScheduler` | 区域调度 |
| `GlobalRegionScheduler` | 全局区域调度 |
| `EntityScheduler` | 实体调度 |

### 禁止项

- ❌ `Bukkit.getScheduler()`
- ❌ `runTask` / `runTaskAsynchronously`
- ❌ 传统 Bukkit 调度 API
- ❌ 初始延迟为 `0L`（Folia 要求 `runAtFixedRate` / `runDelayed` 初始延迟 **≥ 1L**）

### 线程安全规则

1. **实体/方块/世界/区块操作** → 必须在对应区域线程内执行
2. **事件监听** → 禁止跨区域异步操作
3. **命令内世界操作** → 必须用调度包裹

---

## 代码要求

- 简洁高效、无冗余逻辑
- 注释极简、关键位置标注
- 可直接运行、无语法错误
- 仅使用 Paper / Folia API
- 禁止 NMS 或不安全反射

---

## 示例模板

### 区域调度

```java
Location loc = ...;
Bukkit.getRegionScheduler().run(this, loc, task -> {
    // 方块/世界操作
});
```

### 实体调度

```java
Entity entity = ...;
entity.getScheduler().run(this, task -> {
    // 实体操作
}, null);
```

### 全局调度

```java
Bukkit.getGlobalRegionScheduler().run(this, task -> {
    // 全局操作
});
```

---

## 检查清单

- [ ] 未使用 `Bukkit.getScheduler()`
- [ ] 实体操作使用 `EntityScheduler`
- [ ] 方块/世界操作使用 `RegionScheduler`
- [ ] 无跨区域异步操作
- [ ] 代码可编译、无线程安全问题
- [ ] 所有调度初始延迟 ≥ `1L`
