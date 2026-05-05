# 核心架构

## 目录结构

```
src/main/java/com/steve/ai/
├── SteveMod.java              # 模组主入口 (Forge mod)
├── action/                    # 动作执行系统
│   ├── ActionExecutor.java    # 基于 tick 的动作队列处理器
│   ├── CollaborativeBuildManager.java  # 多 Agent 协调
│   ├── Task.java              # 动作任务数据模型
│   └── actions/               # 独立动作实现
├── client/                    # 客户端 GUI
│   ├── SteveGUI.java          # 滑出式面板 GUI (按 K 打开)
│   └── KeyBindings.java
├── command/                   # Minecraft 命令
│   └── SteveCommands.java     # /steve spawn, /steve tell 等
├── config/                     # 配置处理
│   └── SteveConfig.java
├── entity/                     # Minecraft 实体类
│   ├── SteveEntity.java       # 自定义实体 (PathfinderMob)
│   └── SteveManager.java      # 管理所有活跃的 Steves
├── event/                      # 事件总线系统
├── execution/                  # 代码执行引擎
│   ├── CodeExecutionEngine.java  # GraalVM JavaScript 引擎
│   ├── SteveAPI.java          # 脚本安全 API 桥接
│   └── AgentStateMachine.java
├── llm/                        # LLM 集成
│   ├── TaskPlanner.java       # 编排 LLM 调用
│   ├── PromptBuilder.java     # 构建提示词
│   ├── ResponseParser.java    # 解析 LLM 响应
│   ├── OpenAIClient.java, GroqClient.java, GeminiClient.java
│   └── resilience/            # 熔断器、重试、限流
├── memory/                     # 记忆和知识系统
│   ├── SteveMemory.java       # 对话历史
│   └── WorldKnowledge.java    # 世界状态追踪
├── plugin/                     # 插件架构
│   └── ActionRegistry.java    # 动态动作工厂
└── structure/                 # 建筑生成
    └── StructureGenerators.java
```

## 核心组件

### 1. 实体系统 (`SteveEntity`)

自定义实体继承 `PathfinderMob`，支持 Minecraft 原生路径规划。

**属性配置**:
- 生命值: 20
- 移动速度: 0.25
- 攻击力: 8
- 跟随距离: 48

### 2. LLM 集成

支持三个提供商，通过 `TaskPlanner` 统一编排：

| 提供商 | 模型 | 特点 |
|--------|------|------|
| OpenAI | GPT-3.5-turbo | 通用能力强 |
| Groq | llama-3.1-70b | 低延迟 |
| Gemini | gemini-pro | Google 生态 |

**关键特性**:
- 异步非阻塞调用（游戏永不掉帧）
- 40-60% 缓存命中率
- 熔断器模式（故障转移）
- 主提供商失败时自动切换到 Groq

### 3. 动作系统

基于 tick 的增量执行，动作跨多个游戏 tick 完成，防止服务器卡顿。

**插件架构**: 动作通过 `ActionRegistry` 动态注册，支持扩展。

### 4. 多 Agent 协作 (`CollaborativeBuildManager`)

当多个 Steves 协同建造时：
- 结构分为 **4 象限**（西北、东北、西南、东南）
- 每个 Steve claim 一个象限，从底部向上建造
- 使用 `ConcurrentHashMap` 保证线程安全
- Agent 提前完成时动态重平衡

### 5. 代码执行引擎

使用 **GraalVM JavaScript** 引擎执行 LLM 生成的脚本代码。

## 关键设计决策

### 1. Tick-Based Execution
动作在多个游戏 tick 中增量执行，避免阻塞游戏线程。

### 2. Direct Action Execution（而非 ReAct）
LLM 预先生成完整动作序列，而非迭代循环，减少 API 调用和延迟。

### 3. Async Non-Blocking
使用 `CompletableFuture` 确保游戏线程永远不被 LLM 调用阻塞。

### 4. Multi-Agent Coordination
使用确定性空间划分（象限），而非动态协商，提高效率。
