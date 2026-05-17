# 多 Agent 协作

## CollaborativeBuildManager

当多个 Steves 协同建造时，`CollaborativeBuildManager` 负责协调。

## 工作原理

### 1. 结构分块

结构被分为 **4 象限**：
- 西北 (NW)
- 东北 (NE)
- 西南 (SW)
- 东南 (SE)

### 2. 象限分配

每个 Steve claim 一个象限：
- 使用 `ConcurrentHashMap` 保证线程安全
- 原子性 section 分配，防止方块冲突

### 3. 建造顺序

每个象限从底部向上建造，确保结构完整性。

### 4. 动态重平衡

当某个 Agent 提前完成时：
- 系统检测空闲 Agent
- 重新分配剩余 section
- 保持负载均衡

## 线程安全

使用以下机制保证并发安全：
- `ConcurrentHashMap` 存储 agent 状态
- 原子操作进行 section 分配
- 无锁设计避免死锁
