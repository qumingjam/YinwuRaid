# MythicMobs 集成使用指南

## 📖 概述

YinwuRaid 插件现已支持 **MythicMobs** 自定义生物！你可以在灾厄袭击中使用任何 MythicMobs 生物，让袭击更加多样化和具有挑战性。

---

## ✨ 功能特性

- ✅ **完全兼容** - 同时支持原版生物和 MythicMobs 生物
- ✅ **智能检测** - 自动检测 MythicMobs 是否安装
- ✅ **无缝集成** - 配置简单，无需额外设置
- ✅ **属性强化** - MythicMobs 生物会自动应用灾厄等级加成
- ✅ **Folia 兼容** - 所有生成操作都在区域线程中执行

---

## 🔧 配置方法

### 1. 基本语法

在 `raid/level-X.yml` 文件中，使用 `mythicmob:` 前缀来指定 MythicMobs 生物：

```yaml
level-10:
  normal-mobs:
    # 原版生物（正常写法）
    STRAY: 2
    PILLAGER: 2
    
    # MythicMobs 生物（需要前缀）
    mythicmob:ShadowAssassin: 2
    mythicmob:DragonMinion: 1

  elite-mobs:
    # 原版精英
    RAVAGER: 2
    
    # MythicMobs 精英
    mythicmob:AncientGolem: 1
    mythicmob:DemonLord: 1
```

### 2. 配置示例

#### 示例 1：Level 7 配置

```yaml
level-7:
  normal-mobs:
    PILLAGER: 3
    VINDICATOR: 2
    # 添加一个 MythicMobs 刺客
    mythicmob:Ninja: 1
  
  elite-mobs:
    RAVAGER: 1
    # 添加一个 MythicMobs 精英战士
    mythicmob:EliteWarrior: 1
```

#### 示例 2：Level 10 终极难度

```yaml
level-10:
  normal-mobs:
    STRAY: 2
    PILLAGER: 2
    VINDICATOR: 2
    EVOKER: 2
    WITCH: 2
    # MythicMobs 普通怪物
    mythicmob:ShadowAssassin: 3
    mythicmob:UndeadKnight: 2
    mythicmob:DarkMage: 1
  
  elite-mobs:
    RAVAGER: 2
    EVOKER: 1
    GHAST: 1
    ZOGLIN: 1
    # MythicMobs 精英 Boss
    mythicmob:AncientGolem: 1
    mythicmob:DemonLord: 1
    mythicmob:DragonRider: 1
```

---

## 📝 MythicMobs 生物配置要求

确保你的 MythicMobs 插件中有对应的生物定义。

### 示例：MythicMobs 配置文件

在 `plugins/MythicMobs/mobs/` 目录下创建或编辑 YAML 文件：

```yaml
# ShadowAssassin.yml
ShadowAssassin:
  Type: ZOMBIE
  Display: '&4暗影刺客'
  Health: 100
  Damage: 15
  Armor: 5
  Skills:
    - teleport{target=player} ~onAttack 0.3
    - potion{type=INVISIBILITY,duration=100} ~onDamaged 0.2
  Options:
    MovementSpeed: 0.4
    FollowRange: 32

# AncientGolem.yml
AncientGolem:
  Type: IRON_GOLEM
  Display: '&6远古傀儡'
  Health: 500
  Damage: 30
  Armor: 20
  Skills:
    - earthquake{radius=5,damage=10} ~onAttack 0.2
    - summon{mob=StoneGolem;amount=2;r=5} ~onDamaged 0.1
  Options:
    MovementSpeed: 0.2
    KnockbackResistance: 1.0

# DragonMinion.yml
DragonMinion:
  Type: ENDER_DRAGON
  Display: '&5龙裔仆从'
  Health: 200
  Damage: 20
  Skills:
    - fireball{speed=2;damage=15} ~onAttack 0.5
    - levitate{duration=40} ~onAttack 0.2
  Options:
    MovementSpeed: 0.5
    Silent: true
```

---

## ⚙️ 工作原理

### 1. 生物识别

插件会自动识别配置中的生物类型：

- **原版生物**：直接使用 Bukkit API 生成（如 `STRAY`, `PILLAGER`）
- **MythicMobs 生物**：通过反射调用 MythicMobs API 生成（如 `mythicmob:ShadowAssassin`）

### 2. 属性强化

所有生成的灾厄袭击生物（包括 MythicMobs）都会自动应用以下强化：

- ❤️ **生命值加成** - 根据灾厄等级和精英状态
- ⚔️ **攻击力加成** - 根据灾厄等级和精英状态
- 🏃 **移动速度加成** - 固定倍数
- 💪 **体型放大** - 精英怪物体型更大
- ✨ **红色发光** - 所有灾厄生物都有红色发光效果
- 🎯 **AI 优化** - 优先攻击村民和铁傀儡

### 3. 错误处理

- 如果 MythicMobs 未安装，会自动跳过 MythicMobs 生物并输出警告日志
- 如果生物 ID 不存在，会记录错误但不会中断袭击
- 所有错误都会在服务器日志中详细记录

---

## 🔍 调试与验证

### 1. 检查 MythicMobs 状态

启动服务器后，查看控制台日志：

```
[17:12:40 INFO]: [YinwuRaid] §a✓ MythicMobs 集成成功！可以使用自定义生物
```

或者如果未安装：

```
[17:12:40 INFO]: [YinwuRaid] §e⚠ MythicMobs 未安装，将只使用原版生物
```

### 2. 重新加载配置

在游戏中执行：

```
/yinwuraid reload
```

查看输出：

```
[YinwuRaid] §e正在重新加载灾厄袭击配置...
[YinwuRaid] §a✓ 灾厄袭击配置重新加载完成
[YinwuRaid] §a✓ MythicMobs 状态：已启用
```

### 3. 测试生成

触发一个高等级灾厄袭击（7-10 级），观察是否有 MythicMobs 生物生成。

---

## ⚠️ 注意事项

### 1. 性能考虑

- MythicMobs 生物通常比原版生物更消耗资源
- 建议控制每波 MythicMobs 生物的数量（不超过 3-5 只）
- 复杂的技能系统可能导致 TPS 下降

### 2. 兼容性

- **MythicMobs 版本**：当前代码针对 MythicMobs 5.x 版本设计
- **Folia 兼容**：所有生成都已在 RegionScheduler 中执行
- **API 变化**：如果 MythicMobs 更新导致 API 变化，可能需要调整代码

### 3. 平衡性建议

- 不要在同一波次中放置太多高难度 MythicMobs 生物
- 建议混合使用原版和 MythicMobs 生物
- 测试不同等级的难度曲线

### 4. 常见问题

**Q: MythicMobs 生物没有生成？**  
A: 检查以下几点：
1. MythicMobs 插件是否正确安装
2. 生物 ID 是否在 MythicMobs 配置中定义
3. 查看服务器日志是否有错误信息

**Q: MythicMobs 生物太强/太弱？**  
A: 可以在 MythicMobs 配置中调整生物的 Health、Damage、Armor 等属性

**Q: 能否混合使用多个第三方生物插件？**  
A: 目前仅支持 MythicMobs，如需支持其他插件需要额外开发

---

## 📚 进阶技巧

### 1. 创建专属 Boss 波次

```yaml
level-10:
  elite-mobs:
    # 最终 Boss 波次
    mythicmob:FinalBoss: 1
    mythicmob:BossGuardian: 2
```

### 2. 主题化袭击

```yaml
# 亡灵主题
level-8:
  normal-mobs:
    mythicmob:SkeletonArcher: 3
    mythicmob:ZombieKnight: 2
    mythicmob:Wraith: 1

# 恶魔主题
level-9:
  normal-mobs:
    mythicmob:Imp: 3
    mythicmob:Hellhound: 2
    mythicmob:FireDemon: 1
```

### 3. 动态难度

通过调整 MythicMobs 生物的技能和属性，可以创建随灾厄等级递增的难度曲线：

- Level 7: 简单的 MythicMobs 生物
- Level 8: 中等难度的 MythicMobs 生物
- Level 9: 困难的 MythicMobs 生物 + 技能
- Level 10: 终极 Boss + 复杂技能组合

---

## 🆘 技术支持

如果遇到问题：

1. 检查服务器日志中的错误信息
2. 确认 MythicMobs 版本兼容性
3. 验证生物配置是否正确
4. 尝试简化配置进行测试

---

**最后更新**: 2026-05-14  
**插件版本**: YinwuRaid 1.1.0  
**支持版本**: Folia 1.21+ / Paper 1.21+
