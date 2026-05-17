# PromptBuilder - 提示词构建系统

## 概述

PromptBuilder 是 Steve AI 的提示词工程核心，负责构建发送给 LLM 的系统提示词和用户提示词。它将 Steve 的环境状态、玩家命令和游戏模式整合成结构化的提示词，引导 LLM 生成有效的 JSON 动作指令。

## 架构设计

```
PromptBuilder
├── buildSystemPrompt()
│   ├── 游戏模式规则 (创造/生存)
│   ├── 动作定义 (attack, build, mine, follow, pathfind)
│   ├── 结构选项说明
│   └── 示例输入输出
└── buildUserPrompt()
    ├── 环境上下文 (位置、实体、方块、生物群系)
    ├── 背包状态
    └── 玩家命令
```

## 核心设计决策

### 1. 双层提示词结构

系统提示词定义规则，用户提示词提供上下文：

```java
// 系统提示词：定义AI角色和输出格式
public static String buildSystemPrompt() { ... }

// 用户提示词：当前情况和玩家命令
public static String buildUserPrompt(SteveEntity steve, String command, WorldKnowledge worldKnowledge) { ... }
```

**原因**：
- 系统提示词相对稳定，可缓存
- 用户提示词每次请求都变化
- 分离关注点，便于维护

### 2. 创造/生存模式动态规则

根据游戏模式调整材料规则：

```java
boolean creative = SteveConfig.CREATIVE_MODE.get();
String materialRule = creative
    ? "10. CREATIVE MODE: Unlimited materials. NEVER mine before building. Build directly."
    : "10. SURVIVAL MODE: Steve has a 36-slot inventory. Mined blocks go into inventory. Building consumes from inventory. If inventory is empty, mine materials first before building.";
```

**创造模式**：
- 材料无限
- 直接建造，无需采矿
- 跳过材料检查

**生存模式**：
- 36 格背包限制
- 采矿获得材料
- 建造消耗背包材料
- 材料不足时需先采矿

### 3. 严格的 JSON 输出格式

强制 LLM 输出有效 JSON：

```json
{
  "reasoning": "简短想法",
  "plan": "动作描述",
  "tasks": [
    {
      "action": "类型",
      "parameters": { ... }
    }
  ]
}
```

**原因**：
- JSON 易于程序解析
- 避免自然语言歧义
- 结构化便于错误处理

### 4. 丰富的环境上下文

用户提示词包含完整的情境感知：

```java
prompt.append("=== YOUR SITUATION ===\n");
prompt.append("Position: ").append(formatPosition(steve.blockPosition())).append("\n");
prompt.append("Nearby Players: ").append(worldKnowledge.getNearbyPlayerNames()).append("\n");
prompt.append("Nearby Entities: ").append(worldKnowledge.getNearbyEntitiesSummary()).append("\n");
prompt.append("Nearby Blocks: ").append(worldKnowledge.getNearbyBlocksSummary()).append("\n");
prompt.append("Inventory: ").append(formatInventory(steve)).append("\n");
prompt.append("Biome: ").append(worldKnowledge.getBiomeName()).append("\n");
```

**包含信息**：
| 信息 | 来源 | 用途 |
|------|------|------|
| 位置 | `steve.blockPosition()` | 定位和导航 |
| 附近玩家 | `WorldKnowledge.getNearbyPlayerNames()` | 跟随、协作目标 |
| 附近实体 | `WorldKnowledge.getNearbyEntitiesSummary()` | 战斗、交互目标 |
| 附近方块 | `WorldKnowledge.getNearbyBlocksSummary()` | 采矿、建造参考 |
| 背包状态 | `steve.getInventory()` | 材料可用性 |
| 生物群系 | `WorldKnowledge.getBiomeName()` | 环境感知 |

## 系统提示词结构

### 动作定义

| 动作 | 参数格式 | 说明 |
|------|----------|------|
| attack | `{"target": "hostile"}` | 攻击敌对生物 |
| build | `{"structure": "house", "blocks": [...], "dimensions": [...]}` | 建造结构 |
| mine | `{"block": "iron", "quantity": 8}` | 采矿 |
| follow | `{"player": "NAME"}` | 跟随玩家 |
| pathfind | `{"x": 0, "y": 0, "z": 0}` | 导航到位置 |

### 结构类型

| 类型 | 生成方式 | 默认尺寸 |
|------|----------|----------|
| house | NBT 模板 | 自动 |
| oldhouse | NBT 模板 | 自动 |
| powerplant | NBT 模板 | 自动 |
| castle | 程序化 | 14x10x14 |
| tower | 程序化 | 6x6x16 |
| barn | 程序化 | 12x8x14 |
| modern | 程序化 | 可变 |

### 示例输入输出

**输入**: "build a house"
```json
{
  "reasoning": "Building standard house near player",
  "plan": "Construct house",
  "tasks": [{
    "action": "build",
    "parameters": {
      "structure": "house",
      "blocks": ["oak_planks", "cobblestone", "glass_pane"],
      "dimensions": [9, 6, 9]
    }
  }]
}
```

**输入**: "get me iron"
```json
{
  "reasoning": "Mining iron ore for player",
  "plan": "Mine iron",
  "tasks": [{
    "action": "mine",
    "parameters": {
      "block": "iron",
      "quantity": 16
    }
  }]
}
```

**输入**: "kill mobs"
```json
{
  "reasoning": "Hunting hostile creatures",
  "plan": "Attack hostiles",
  "tasks": [{
    "action": "attack",
    "parameters": {
      "target": "hostile"
    }
  }]
}
```

## 用户提示词格式

### 完整示例

```
=== YOUR SITUATION ===
Position: [128, 64, -45]
Nearby Players: Alice, Bob
Nearby Entities: 3 Sheep, 1 Cow, 2 Chicken
Nearby Blocks: grass_block, dirt, oak_log, stone, iron_ore
Inventory: Iron Ingot x16, Oak Planks x32, Cobblestone x64
Biome: forest

=== PLAYER COMMAND ===
"build a house here"

=== YOUR RESPONSE (with reasoning) ===
```

### 背包格式化

```java
private static String formatInventory(SteveEntity steve) {
    SimpleContainer inventory = steve.getInventory();
    Map<String, Integer> itemCounts = new HashMap<>();

    for (int i = 0; i < inventory.getContainerSize(); i++) {
        ItemStack stack = inventory.getItem(i);
        if (!stack.isEmpty()) {
            String name = stack.getHoverName().getString();
            itemCounts.merge(name, stack.getCount(), Integer::sum);
        }
    }

    if (itemCounts.isEmpty()) {
        return "[empty]";
    }

    // 输出格式: "ItemName xCount, ItemName xCount"
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
        if (sb.length() > 0) sb.append(", ");
        sb.append(entry.getKey()).append(" x").append(entry.getValue());
    }
    return sb.toString();
}
```

**输出格式**：
- 空背包：`[empty]`
- 有物品：`Iron Ingot x16, Oak Planks x32, Cobblestone x64`
- 创造模式：`[unlimited - creative mode]`

### 位置格式化

```java
private static String formatPosition(BlockPos pos) {
    return String.format("[%d, %d, %d]", pos.getX(), pos.getY(), pos.getZ());
}
```

**输出格式**：`[128, 64, -45]`

## 提示词优化策略

### 1. Token 效率

- 位置使用整数坐标，避免浮点数
- 实体摘要限制前 5 种
- 方块摘要限制前 5 种
- 背包合并相同物品

### 2. 上下文相关性

- 仅在生存模式显示背包详情
- 仅显示非空气方块
- 仅显示玩家类型实体（过滤掉其他 Steve）

### 3. 指令清晰度

- 使用 `===` 分隔符区分不同部分
- 引号包裹玩家命令
- 明确要求 JSON 输出

## 与 LLM 集成

### 调用流程

```java
// 1. 构建系统提示词（可缓存）
String systemPrompt = PromptBuilder.buildSystemPrompt();

// 2. 构建用户提示词（每次变化）
String userPrompt = PromptBuilder.buildUserPrompt(steve, command, worldKnowledge);

// 3. 发送到 LLM
String response = llmClient.sendRequest(systemPrompt, userPrompt);

// 4. 解析响应
Task[] tasks = ResponseParser.parse(response);
```

### 缓存策略

系统提示词可缓存，因为：
- 不依赖游戏状态
- 仅在配置变化时改变
- 减少重复构建开销

用户提示词不可缓存，因为：
- 包含实时位置信息
- 包含当前背包状态
- 包含附近实体信息

## 已知限制

1. **语言限制**：提示词全英文，中文命令可能理解不准确
2. **上下文长度**：背包满时提示词可能过长
3. **动态规则**：无法根据游戏进程调整规则
4. **多动作支持**：一次只能执行一个主要动作
5. **空间感知**：无法描述复杂的空间关系

## 扩展建议

1. **多语言支持**：添加中文系统提示词选项
2. **上下文压缩**：背包物品智能摘要
3. **动态示例**：根据玩家历史命令调整示例
4. **复杂任务**：支持多步骤任务分解
5. **记忆集成**：将历史动作纳入提示词

## 配置选项

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `CREATIVE_MODE` | Boolean | false | 创造模式开关 |
| `AI_PROVIDER` | String | "groq" | LLM 提供商 |
| `MAX_TOKENS` | Integer | 8000 | 最大 token 数 |
| `TEMPERATURE` | Double | 0.7 | 生成温度 |
