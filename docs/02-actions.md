# 动作系统

## 概述

动作系统是 Steve AI 的核心执行单元，负责在 Minecraft 世界中执行具体任务。

## 核心类

- `ActionExecutor.java` - 基于 tick 的动作队列处理器
- `Task.java` - 动作任务数据模型
- `CollaborativeBuildManager.java` - 多 Agent 协调

## 可用动作

| 动作 | 功能 |
|------|------|
| `MineBlockAction` | 智能采矿，带路径规划 |
| `BuildStructureAction` | 程序化建筑和模板建筑 |
| `PlaceBlockAction` | 单方块放置（带验证） |
| `PathfindAction` | 导航到坐标 |
| `CombatAction` | 目标战斗 |
| `FollowPlayerAction` | 跟随玩家 |
| `CraftItemAction` | 物品合成 |
| `GatherResourceAction` | 资源采集 |

## 执行流程

1. 用户发送自然语言指令（如 `/steve tell miner1 开采 20 铁矿石`）
2. `TaskPlanner` 调用 LLM 解析指令，生成动作序列
3. 动作被加入 `ActionExecutor` 的队列
4. 每个游戏 tick，`ActionExecutor` 处理队列中的动作
5. 动作执行结果通过 GUI 显示给用户

## 插件架构

动作通过 `ActionRegistry` 动态注册，支持自定义扩展。

```java
// 注册新动作
ActionRegistry.register("custom_action", CustomAction.class);
```
