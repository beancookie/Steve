# Steve AI - Minecraft AI Agent Mod

## 项目概述

**Steve AI** 是一个 Minecraft Forge 1.20.1 模组，将 AI 驱动的自主 agents（称为 "Steves"）引入游戏世界。用户可以通过命令生成 Steves 并给予自然语言指令，如"开采 20 铁矿石"或"在我附近建一座房子"。Steves 使用大语言模型（OpenAI GPT、Groq 或 Google Gemini）来理解指令、规划行动并在 Minecraft 世界中执行。

**版本**: 1.0.0
**Minecraft 版本**: 1.20.1
**Java 版本**: 17

## 快速开始

### 命令

| 命令 | 功能 |
|------|------|
| `/steve spawn <name>` | 生成新的 Steve |
| `/steve remove <name>` | 移除 Steve |
| `/steve list` | 列出所有活跃的 Steves |
| `/steve stop <name>` | 停止当前动作 |
| `/steve tell <name> <command>` | 发送自然语言指令 |

### 示例

```
/steve spawn miner1
/steve tell miner1 开采 20 铁矿石
/steve tell miner1 在我附近建一座房子
/steve tell miner1 保护我免受僵尸攻击
```

### GUI

按 **K** 打开右侧滑出面板，可滚动消息历史，支持命令历史（上下箭头）。

颜色区分:
- 🟢 绿色: 用户消息
- 🔵 蓝色: Steve 响应
- 🟠 橙色: 系统消息
