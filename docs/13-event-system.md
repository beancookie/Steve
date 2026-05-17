# 事件系统 - 解耦的组件通信

## 概述

事件系统实现了观察者模式（Observer Pattern），用于组件间的解耦通信。发布者不需要知道订阅者的存在，支持同步/异步发布、优先级排序和错误隔离。

## 架构设计

```
事件系统
├── EventBus (接口)
│   ├── subscribe() → 订阅事件
│   ├── publish() → 同步发布
│   ├── publishAsync() → 异步发布
│   └── unsubscribe() → 取消订阅
├── SimpleEventBus (实现)
│   ├── ConcurrentHashMap 存储订阅者
│   ├── CopyOnWriteArrayList 线程安全列表
│   └── ExecutorService 异步执行器
└── 事件类型
    ├── ActionStartedEvent
    ├── ActionCompletedEvent
    └── 自定义事件...
```

## 核心设计决策

### 1. 观察者模式

发布者和订阅者完全解耦：

```java
// 发布者（不知道订阅者的存在）
eventBus.publish(new ActionStartedEvent("mine", "Mining stone"));

// 订阅者（不知道发布者的存在）
eventBus.subscribe(ActionStartedEvent.class, event -> {
    System.out.println("Action started: " + event.getActionName());
});
```

**优势**：
- 松耦合
- 易于扩展
- 单一职责

### 2. 优先级订阅

高优先级订阅者先执行：

```java
// 高优先级（先执行）
eventBus.subscribe(ActionStartedEvent.class, event -> {
    // 日志记录（优先级 100）
}, 100);

// 低优先级（后执行）
eventBus.subscribe(ActionStartedEvent.class, event -> {
    // 统计收集（优先级 0）
}, 0);
```

**优先级排序**：
```java
// 按优先级降序排序（高优先级先执行）
list.sort((a, b) -> Integer.compare(b.priority, a.priority));
```

### 3. 同步/异步发布

```java
// 同步发布（在调用线程执行）
eventBus.publish(new ActionStartedEvent("mine", "Mining stone"));

// 异步发布（在独立线程执行）
eventBus.publishAsync(new ActionCompletedEvent("mine", true));
```

**异步执行器**：
```java
private final ExecutorService asyncExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "event-bus-async");
    t.setDaemon(true);  // 守护线程，不阻止 JVM 退出
    return t;
});
```

### 4. 错误隔离

单个订阅者的错误不影响其他订阅者：

```java
for (SubscriberEntry<?> entry : subs) {
    try {
        ((Consumer<T>) entry.subscriber).accept(event);
    } catch (Exception e) {
        LOGGER.error("Error in subscriber: {}", e.getMessage());
        // 继续执行其他订阅者
    }
}
```

### 5. 线程安全

使用 `CopyOnWriteArrayList` 和 `ConcurrentHashMap`：

```java
// 存储结构
private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<SubscriberEntry<?>>> subscribers;

// 订阅时
subscribers.compute(eventType, (key, list) -> {
    if (list == null) {
        list = new CopyOnWriteArrayList<>();
    }
    list.add(entry);
    list.sort((a, b) -> Integer.compare(b.priority, a.priority));
    return list;
});
```

**CopyOnWriteArrayList 特点**：
- 写时复制（写操作创建新数组）
- 读操作无锁
- 适合读多写少场景

## 事件类型

### 内置事件

```java
// 动作开始事件
public class ActionStartedEvent {
    private final String actionName;
    private final String description;
    private final SteveEntity steve;
}

// 动作完成事件
public class ActionCompletedEvent {
    private final String actionName;
    private final boolean success;
    private final String message;
    private final SteveEntity steve;
}
```

### 自定义事件

```java
// 定义事件类
public class BuildCompletedEvent {
    private final String structureType;
    private final BlockPos position;
    private final int blockCount;

    // 构造函数、getter...
}

// 发布事件
eventBus.publish(new BuildCompletedEvent("castle", pos, 1200));

// 订阅事件
eventBus.subscribe(BuildCompletedEvent.class, event -> {
    LOGGER.info("Built {} at {} with {} blocks",
        event.getStructureType(),
        event.getPosition(),
        event.getBlockCount());
});
```

## 订阅管理

### 订阅句柄

返回 `Subscription` 对象用于取消订阅：

```java
// 订阅并获取句柄
EventBus.Subscription subscription = eventBus.subscribe(
    ActionStartedEvent.class,
    event -> handleAction(event)
);

// 取消订阅
subscription.unsubscribe();

// 检查是否活跃
if (subscription.isActive()) {
    // 仍然订阅中
}
```

### 批量取消

```java
// 取消特定事件类型的所有订阅
eventBus.unsubscribeAll(ActionStartedEvent.class);

// 清空所有订阅
eventBus.clear();
```

## 使用示例

### 日志记录

```java
public class ActionLogger {
    private final EventBus eventBus;

    public ActionLogger(EventBus eventBus) {
        this.eventBus = eventBus;
        subscribe();
    }

    private void subscribe() {
        eventBus.subscribe(ActionStartedEvent.class, event -> {
            LOGGER.info("[ACTION] Started: {} - {}",
                event.getSteve().getSteveName(),
                event.getDescription());
        }, 100);  // 高优先级

        eventBus.subscribe(ActionCompletedEvent.class, event -> {
            if (event.isSuccess()) {
                LOGGER.info("[ACTION] Completed: {}", event.getActionName());
            } else {
                LOGGER.warn("[ACTION] Failed: {} - {}",
                    event.getActionName(), event.getMessage());
            }
        }, 100);
    }
}
```

### 统计收集

```java
public class ActionStatistics {
    private final Map<String, Integer> actionCounts = new HashMap<>();
    private final Map<String, Long> actionDurations = new HashMap<>();

    public ActionStatistics(EventBus eventBus) {
        eventBus.subscribe(ActionStartedEvent.class, event -> {
            actionCounts.merge(event.getActionName(), 1, Integer::sum);
            // 记录开始时间
        }, 0);  // 低优先级

        eventBus.subscribe(ActionCompletedEvent.class, event -> {
            // 计算持续时间
        }, 0);
    }
}
```

### GUI 更新

```java
public class GUIUpdater {
    public GUIUpdater(EventBus eventBus, SteveGUI gui) {
        eventBus.subscribe(ActionCompletedEvent.class, event -> {
            // 在客户端线程更新 GUI
            Minecraft.getInstance().execute(() -> {
                if (event.isSuccess()) {
                    gui.addSteveMessage("完成: " + event.getMessage());
                } else {
                    gui.addSystemMessage("失败: " + event.getMessage());
                }
            });
        });
    }
}
```

## 异步发布

### 使用场景

```java
// 不阻塞游戏线程
eventBus.publishAsync(new ActionCompletedEvent("mine", true));

// 异步处理
eventBus.subscribe(ActionCompletedEvent.class, event -> {
    // 可能耗时的操作
    saveStatistics(event);
    updateDatabase(event);
});
```

### 错误处理

```java
asyncExecutor.submit(() -> {
    try {
        publish(event);
    } catch (Exception e) {
        LOGGER.error("Error in async publishing: {}", e.getMessage());
    }
});
```

## 性能考虑

### 写时复制开销

`CopyOnWriteArrayList` 的写操作（订阅/取消订阅）有开销：
- 创建新数组
- 复制所有元素
- 替换引用

**适用场景**：读多写少（订阅后频繁发布）

**不适用场景**：频繁订阅/取消订阅

### 内存占用

每个订阅者占用：
- `SubscriberEntry` 对象（~32 字节）
- `Consumer` lambda（~64 字节）
- 数组引用（~8 字节）

**总计**：~100 字节/订阅者

### 发布性能

同步发布：
- O(n) 遍历订阅者
- n = 订阅者数量
- 通常 < 1μs（10 个订阅者）

异步发布：
- 提交到线程池
- 立即返回
- 异步执行

## 线程安全

### 保证

1. **订阅/取消订阅**：线程安全（ConcurrentHashMap + CopyOnWriteArrayList）
2. **发布**：线程安全（遍历快照）
3. **订阅者执行**：在发布线程执行（同步）或独立线程（异步）

### 潜在问题

```java
// 问题：订阅者在事件处理中取消订阅
eventBus.subscribe(ActionStartedEvent.class, event -> {
    subscription.unsubscribe();  // 可能导致 ConcurrentModificationException
});
```

**解决方案**：CopyOnWriteArrayList 遍历快照，安全

## 与其他系统集成

### 与 PluginManager 集成

```java
public class CoreActionsPlugin implements ActionPlugin {
    @Override
    public void onLoad(ActionRegistry registry, ServiceContainer container) {
        EventBus eventBus = container.getService(EventBus.class);

        // 注册动作并在执行时发布事件
        registry.register("mine", (steve, task, ctx) -> {
            MineBlockAction action = new MineBlockAction(steve, task);
            return new EventPublishingAction(action, eventBus);
        });
    }
}
```

### 与 SteveEntity 集成

```java
public class SteveEntity extends PathfinderMob {
    private final EventBus eventBus;

    public void onActionStarted(BaseAction action) {
        eventBus.publish(new ActionStartedEvent(
            action.getClass().getSimpleName(),
            action.getDescription(),
            this
        ));
    }
}
```

## 已知限制

1. **无事件过滤**：无法按条件过滤事件
2. **无事件转换**：无法在传递过程中修改事件
3. **无死信队列**：异步事件失败后丢失
4. **无事件重放**：无法重放历史事件

## 扩展建议

1. **事件过滤**：支持谓词过滤
2. **事件转换**：支持事件映射和转换
3. **死信队列**：失败事件的重试机制
4. **事件持久化**：保存事件历史
5. **分布式事件**：跨进程事件传递

## 配置

无额外配置。事件系统自动初始化。

## 调试

```java
// 获取订阅者数量
int count = eventBus.getSubscriberCount(ActionStartedEvent.class);
LOGGER.debug("ActionStartedEvent subscribers: {}", count);

// 列出所有事件类型
eventBus.subscribers.keySet().forEach(type ->
    LOGGER.debug("Event type: {}", type.getSimpleName()));
```

## 最佳实践

1. **事件不可变**：事件对象应为不可变
2. **细粒度事件**：每个事件只携带必要信息
3. **优先级使用**：日志/监控用高优先级，统计用低优先级
4. **避免阻塞**：同步发布时避免长时间阻塞
5. **及时取消**：组件销毁时取消订阅
