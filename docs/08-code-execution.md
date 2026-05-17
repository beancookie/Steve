# 代码执行引擎

## 概述

Steve AI 使用 **GraalVM JavaScript** 引擎执行 LLM 生成的脚本代码。

## 核心组件

- `CodeExecutionEngine.java` - GraalVM 引擎封装
- `SteveAPI.java` - 安全 API 桥接
- `AgentStateMachine.java` - Agent 状态机
- `InterceptorChain.java` - 日志、指标、事件拦截器

## SteveAPI

暴露给脚本的安全操作：

```javascript
// 移动
steve.moveTo(x, y, z);

// 建造
steve.build("cobblestone", count);

// 采矿
steve.mine("iron_ore");

// 攻击
steve.attack(entity);

// 合成
steve.craft("iron_pickaxe");

// 放置
steve.place("torch", x, y, z);
```

## 沙箱环境

脚本运行在受限的沙箱环境中，只允许安全的 Minecraft 操作。

## 拦截器链

`InterceptorChain` 在执行前后插入拦截器：
- 日志记录
- 指标收集
- 事件发布
