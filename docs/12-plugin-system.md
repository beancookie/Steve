# 插件系统 - 可扩展的动作注册框架

## 概述

插件系统使用 Java SPI（Service Provider Interface）机制实现动作的动态发现和加载。开发者可以通过实现 `ActionPlugin` 接口来扩展 Steve 的功能，无需修改核心代码。

## 架构设计

```
插件系统
├── ActionPlugin (SPI 接口)
│   ├── getPluginId() → 唯一标识符
│   ├── onLoad() → 注册动作
│   ├── onUnload() → 清理资源
│   ├── getPriority() → 加载优先级
│   └── getDependencies() → 依赖声明
├── PluginManager (插件管理器)
│   ├── ServiceLoader 发现
│   ├── 拓扑排序（依赖解析）
│   └── 生命周期管理
├── ActionRegistry (动作注册表)
│   ├── register() → 注册动作工厂
│   └── create() → 创建动作实例
└── CoreActionsPlugin (核心插件)
    └── 注册所有内置动作
```

## 核心设计决策

### 1. SPI 服务发现

使用 Java ServiceLoader 自动发现插件：

```java
// 发现所有实现 ActionPlugin 的类
ServiceLoader<ActionPlugin> loader = ServiceLoader.load(ActionPlugin.class);

for (ActionPlugin plugin : loader) {
    discovered.add(plugin);
}
```

**配置文件位置**：
```
src/main/resources/META-INF/services/com.steve.ai.plugin.ActionPlugin
```

**文件内容示例**：
```
com.steve.ai.plugin.CoreActionsPlugin
com.example.CustomActionsPlugin
```

**优势**：
- 零配置发现
- 编译时确定
- 无反射开销

### 2. 依赖解析（拓扑排序）

使用拓扑排序确保插件按依赖顺序加载：

```java
private List<ActionPlugin> sortPlugins(List<ActionPlugin> plugins) {
    // 构建依赖图
    Map<String, Set<String>> dependencies = new HashMap<>();
    Map<String, Integer> inDegree = new HashMap<>();

    // 计算入度
    for (ActionPlugin plugin : plugins) {
        for (String dep : plugin.getDependencies()) {
            if (pluginMap.containsKey(dep)) {
                inDegree.merge(plugin.getPluginId(), 1, Integer::sum);
            }
        }
    }

    // 拓扑排序（使用优先队列处理优先级）
    PriorityQueue<ActionPlugin> queue = new PriorityQueue<>(
        Comparator.comparingInt(ActionPlugin::getPriority).reversed());

    // 从无依赖的插件开始
    for (ActionPlugin plugin : plugins) {
        if (inDegree.get(plugin.getPluginId()) == 0) {
            queue.offer(plugin);
        }
    }

    // 处理队列
    while (!queue.isEmpty()) {
        ActionPlugin plugin = queue.poll();
        sorted.add(plugin);

        // 更新依赖此插件的其他插件
        for (ActionPlugin other : plugins) {
            if (dependencies.get(other.getPluginId()).contains(plugin.getPluginId())) {
                int newDegree = inDegree.get(other.getPluginId()) - 1;
                if (newDegree == 0) {
                    queue.offer(other);
                }
            }
        }
    }

    return sorted;
}
```

**依赖示例**：
```java
public class CombatAIPlugin implements ActionPlugin {
    @Override
    public String[] getDependencies() {
        return new String[] { "core-actions" };  // 依赖核心插件
    }
}
```

### 3. 优先级系统

控制同名动作的覆盖顺序：

| 优先级范围 | 用途 | 示例 |
|-----------|------|------|
| 1000+ | 核心/必需插件 | CoreActionsPlugin |
| 500-999 | 高优先级插件 | 内置扩展 |
| 0-499 | 普通插件（默认） | 用户插件 |
| 负数 | 低优先级/覆盖插件 | 替换默认行为 |

```java
public class OverridePlugin implements ActionPlugin {
    @Override
    public int getPriority() {
        return -100;  // 低优先级，允许被覆盖
    }
}
```

### 4. 工厂模式注册

插件注册动作工厂，而非直接实例化：

```java
@Override
public void onLoad(ActionRegistry registry, ServiceContainer container) {
    // 注册工厂 lambda
    registry.register("dance", (steve, task, ctx) -> new DanceAction(steve, task));

    // 带依赖注入的工厂
    registry.register("smart_mine", (steve, task, ctx) -> {
        LLMCache cache = container.getService(LLMCache.class);
        return new SmartMineAction(steve, task, cache);
    });
}
```

**优势**：
- 延迟实例化
- 支持依赖注入
- 便于测试

## 插件生命周期

```
服务器启动
    │
    ├─ ServiceLoader 发现插件
    │
    ├─ 拓扑排序（考虑依赖和优先级）
    │
    ├─ 按顺序加载插件
    │   ├─ 检查依赖是否已加载
    │   ├─ 调用 onLoad(registry, container)
    │   └─ 记录到已加载列表
    │
    ├─ 插件运行中
    │   └─ 注册的动作可用
    │
    └─ 服务器关闭
        └─ 按相反顺序调用 onUnload()
```

## ActionRegistry 实现

### 注册动作工厂

```java
public class ActionRegistry {
    private final Map<String, ActionFactory> factories = new ConcurrentHashMap<>();

    public void register(String actionName, ActionFactory factory) {
        if (factories.containsKey(actionName)) {
            LOGGER.warn("Overwriting existing action: {}", actionName);
        }
        factories.put(actionName.toLowerCase(), factory);
    }

    public BaseAction create(SteveEntity steve, Task task, ActionContext ctx) {
        String actionName = task.getAction().toLowerCase();
        ActionFactory factory = factories.get(actionName);

        if (factory == null) {
            LOGGER.warn("Unknown action: {}", actionName);
            return null;
        }

        return factory.create(steve, task, ctx);
    }
}
```

### ActionFactory 接口

```java
@FunctionalInterface
public interface ActionFactory {
    BaseAction create(SteveEntity steve, Task task, ActionContext ctx);
}
```

## CoreActionsPlugin

核心插件注册所有内置动作：

```java
public class CoreActionsPlugin implements ActionPlugin {
    @Override
    public String getPluginId() {
        return "core-actions";
    }

    @Override
    public int getPriority() {
        return 1000;  // 最高优先级
    }

    @Override
    public void onLoad(ActionRegistry registry, ServiceContainer container) {
        registry.register("pathfind", (steve, task, ctx) -> new PathfindAction(steve, task));
        registry.register("mine", (steve, task, ctx) -> new MineBlockAction(steve, task));
        registry.register("place", (steve, task, ctx) -> new PlaceBlockAction(steve, task));
        registry.register("craft", (steve, task, ctx) -> new CraftItemAction(steve, task));
        registry.register("attack", (steve, task, ctx) -> new CombatAction(steve, task));
        registry.register("follow", (steve, task, ctx) -> new FollowPlayerAction(steve, task));
        registry.register("gather", (steve, task, ctx) -> new GatherResourceAction(steve, task));
        registry.register("build", (steve, task, ctx) -> new BuildStructureAction(steve, task));
    }
}
```

## 自定义插件示例

### 步骤 1：实现 ActionPlugin

```java
package com.example;

import com.steve.ai.plugin.ActionPlugin;
import com.steve.ai.plugin.ActionRegistry;
import com.steve.ai.di.ServiceContainer;

public class DancePlugin implements ActionPlugin {
    @Override
    public String getPluginId() {
        return "dance-plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public void onLoad(ActionRegistry registry, ServiceContainer container) {
        registry.register("dance", (steve, task, ctx) -> new DanceAction(steve, task));
        registry.register("wave", (steve, task, ctx) -> new WaveAction(steve, task));
    }

    @Override
    public void onUnload() {
        // 清理资源
    }
}
```

### 步骤 2：创建 SPI 配置文件

**文件**: `src/main/resources/META-INF/services/com.steve.ai.plugin.ActionPlugin`

```
com.example.DancePlugin
```

### 步骤 3：实现动作类

```java
public class DanceAction extends BaseAction {
    private int danceTicks = 0;

    public DanceAction(SteveEntity steve, Task task) {
        super(steve, task);
    }

    @Override
    protected void onStart() {
        steve.sendActionBarMessage("开始跳舞！");
    }

    @Override
    protected void onTick() {
        danceTicks++;

        // 每 10 tick 跳一次
        if (danceTicks % 10 == 0) {
            steve.swing(InteractionHand.MAIN_HAND, true);
            steve.setJumping(true);
        }

        // 跳 100 tick 后完成
        if (danceTicks >= 100) {
            result = ActionResult.success("跳舞完成！");
        }
    }

    @Override
    protected void onCancel() {
        steve.setJumping(false);
    }

    @Override
    public String getDescription() {
        return "跳舞";
    }
}
```

## 依赖注入集成

插件可通过 ServiceContainer 获取共享服务：

```java
@Override
public void onLoad(ActionRegistry registry, ServiceContainer container) {
    // 获取已注册的服务
    LLMCache cache = container.getService(LLMCache.class);
    EventBus eventBus = container.getService(EventBus.class);

    // 注册带依赖的动作
    registry.register("smart_build", (steve, task, ctx) ->
        new SmartBuildAction(steve, task, cache, eventBus));
}
```

## 错误处理

### 插件加载失败

```java
for (ActionPlugin plugin : sorted) {
    try {
        loadPlugin(plugin, registry, container);
    } catch (Exception e) {
        LOGGER.error("Failed to load plugin {}: {}", plugin.getPluginId(), e.getMessage());
        // 继续加载其他插件
    }
}
```

### 循环依赖检测

```java
if (sorted.size() != plugins.size()) {
    LOGGER.error("Circular dependency detected!");
    // 加载剩余插件（忽略依赖）
    for (ActionPlugin plugin : plugins) {
        if (!processed.contains(plugin.getPluginId())) {
            sorted.add(plugin);
        }
    }
}
```

## 线程安全

- `ConcurrentHashMap` 存储已加载插件
- 插件加载在服务器线程执行（非线程安全）
- 动作注册表使用 `ConcurrentHashMap`

## 已知限制

1. **单例插件**：每个插件 ID 只能有一个实例
2. **无热重载**：插件变更需重启服务器
3. **依赖解析**：不支持可选依赖
4. **版本管理**：无版本冲突检测

## 扩展建议

1. **热重载**：支持运行时重新加载插件
2. **版本管理**：语义版本冲突检测
3. **可选依赖**：支持 `optional` 依赖声明
4. **插件隔离**：类加载器隔离防止冲突
5. **插件市场**：在线插件仓库

## 配置

无额外配置。插件通过 SPI 自动发现。

## 调试

启用插件系统日志：

```properties
# log4j2.properties
logger.plugin.name = com.steve.ai.plugin
logger.plugin.level = DEBUG
```

日志输出示例：
```
[INFO] Discovering plugins via ServiceLoader...
[INFO] Discovered plugin: core-actions v1.0.0 (priority: 1000)
[INFO] Discovered plugin: dance-plugin v1.0.0 (priority: 0)
[INFO] Loading plugin: core-actions v1.0.0
[INFO] Plugin core-actions loaded successfully
[INFO] Loading plugin: dance-plugin v1.0.0
[INFO] Plugin dance-plugin loaded successfully
[INFO] Plugin loading complete: 2 plugins loaded
```
