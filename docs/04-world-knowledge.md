# WorldKnowledge - 世界感知系统

## 概述

WorldKnowledge 是 Steve 智能体的环境感知模块，负责扫描周围世界并提取关键信息，包括生物群系、方块分布和附近实体。这些信息为 LLM 决策提供环境上下文。

## 架构设计

```
WorldKnowledge
├── 生物群系扫描 (scanBiome)
│   └── 获取当前位置的生物群系名称
├── 方块扫描 (scanBlocks)
│   └── 统计扫描半径内的方块类型和数量
├── 实体扫描 (scanEntities)
│   └── 获取扫描范围内的所有实体
└── 摘要生成
    ├── getNearbyBlocksSummary() → 前5种方块
    ├── getNearbyEntitiesSummary() → 前5种实体
    └── getNearbyPlayerNames() → 附近玩家列表
```

## 核心设计决策

### 1. 构造时扫描

在创建时立即执行完整扫描：

```java
public WorldKnowledge(SteveEntity steve) {
    this.steve = steve;
    scan();  // 立即扫描
}
```

**原因**：确保获取的是当前时刻的环境快照，避免过时数据。

### 2. 采样式扫描

使用步长为 2 的采样，而非逐方块扫描：

```java
for (int x = -scanRadius; x <= scanRadius; x += 2) {
    for (int y = -scanRadius; y <= scanRadius; y += 2) {
        for (int z = -scanRadius; z <= scanRadius; z += 2) {
            // 每隔一个方块采样
        }
    }
}
```

**原因**：
- 扫描半径 16 格，完整扫描需检查 33³ = 35,937 个方块
- 采样后仅需检查约 17³ = 4,913 个方块（减少 86%）
- 牺牲少量精度换取大幅性能提升

### 3. 过滤空气方块

排除三种空气类型：

```java
if (block != Blocks.AIR && block != Blocks.CAVE_AIR && block != Blocks.VOID_AIR) {
    nearbyBlocks.put(block, nearbyBlocks.getOrDefault(block, 0) + 1);
}
```

**原因**：空气占绝大多数，过滤后仅保留有意义的方块。

### 4. 排序摘要

摘要按数量降序排列，限制返回前 5 项：

```java
List<Map.Entry<Block, Integer>> sorted = nearbyBlocks.entrySet().stream()
    .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
    .limit(5)
    .toList();
```

**原因**：LLM 上下文窗口有限，优先展示最重要的环境信息。

## 扫描参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `scanRadius` | 16 | 扫描半径（格） |
| 采样步长 | 2 | 每隔 2 格采样一次 |
| 方块摘要 | 前 5 种 | 按数量排序 |
| 实体摘要 | 前 5 种 | 按类型分组 |

## 扫描流程

```
构造 WorldKnowledge
    │
    ├─ scanBiome()
    │   ├─ 获取当前位置 BlockPos
    │   ├─ 从 Level 获取 Biome 对象
    │   ├─ 从注册表获取 BiomeKey
    │   └─ 提取路径名称 (如 "plains", "desert")
    │
    ├─ scanBlocks()
    │   ├─ 创建空 HashMap<Block, Integer>
    │   ├─ 遍历 16x16x16 区域 (步长2)
    │   ├─ 过滤空气方块
    │   └─ 统计每种方块数量
    │
    └─ scanEntities()
        ├─ 创建膨胀 16 格的 AABB
        └─ 获取区域内所有实体
```

## 数据结构

### 方块统计

```java
Map<Block, Integer> nearbyBlocks
// 示例: {STONE=120, DIRT=85, GRASS_BLOCK=42, OAK_LOG=15, COAL_ORE=8}
```

### 实体列表

```java
List<Entity> nearbyEntities
// 示例: [Player, Zombie, Cow, Sheep, Creeper]
```

## 摘要输出格式

### 方块摘要

```
"石头, 泥土, 草方块, 橡木原木, 煤矿石"
```

- 仅显示方块名称，不含数量
- 按数量降序排列
- 最多 5 种

### 实体摘要

```
"2 Zombie, 1 Cow, 1 Sheep, 1 Creeper"
```

- 格式：`数量 实体类型`
- 按类型分组计数
- 最多 5 种

### 玩家名称

```
"Steve, Alex"
```

- 仅包含 Player 类型实体
- 逗号分隔
- 无玩家时返回 "none"

## 性能分析

### 时间复杂度

| 操作 | 复杂度 | 说明 |
|------|--------|------|
| scanBiome | O(1) | 单次查表 |
| scanBlocks | O(n³) | n = scanRadius/2 = 8，约 512 次迭代 |
| scanEntities | O(m) | m = 区域内实体数 |

### 空间复杂度

| 数据结构 | 大小 | 说明 |
|----------|------|------|
| nearbyBlocks | ≤ 扫描方块种类数 | 通常 < 50 |
| nearbyEntities | 区域内实体数 | 通常 < 100 |

## 与 LLM 集成

WorldKnowledge 的数据通常用于构建 LLM 提示的环境部分：

```java
String prompt = String.format(
    "你在 %s 生物群系。" +
    "附近有: %s。" +
    "附近实体: %s。" +
    "附近玩家: %s。",
    worldKnowledge.getBiomeName(),
    worldKnowledge.getNearbyBlocksSummary(),
    worldKnowledge.getNearbyEntitiesSummary(),
    worldKnowledge.getNearbyPlayerNames()
);
```

## 已知限制

1. **静态快照**：创建后不自动更新，需要重新创建实例
2. **采样精度**：步长 2 可能遗漏稀有方块
3. **无高度优先**：垂直方向与水平方向同等采样密度
4. **实体过滤**：返回所有实体，未区分友好/敌对
5. **无方块状态**：仅记录方块类型，不包含状态（如红石信号）

## 扩展建议

1. **增量更新**：添加 `refresh()` 方法，仅扫描变化区域
2. **实体分类**：分离友好实体、敌对实体、中立实体
3. **危险检测**：标记岩浆、悬崖等危险区域
4. **资源定位**：记录特定资源的精确位置
5. **高度图**：生成地形高度概览
