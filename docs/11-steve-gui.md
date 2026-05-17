# SteveGUI - 侧边栏聊天界面

## 概述

SteveGUI 是一个用于与 Steve 智能体交互的侧边栏聊天面板，灵感来自 Cursor 的 composer UI。它从屏幕右侧滑入/滑出，带有平滑动画效果。

## 架构设计

```
SteveGUI (静态类)
├── 渲染层 (onRenderOverlay)
│   ├── 面板背景和边框
│   ├── 头部 ("Steve AI")
│   ├── 消息历史 (可滚动)
│   │   ├── 用户气泡 (绿色，右对齐)
│   │   ├── Steve 气泡 (蓝色，左对齐)
│   │   └── 系统气泡 (橙色，左对齐)
│   └── 输入区域 (EditBox)
├── 输入处理
│   ├── 按键 (Enter, Escape, 方向键)
│   ├── 字符输入
│   ├── 鼠标点击
│   └── 鼠标滚轮
└── 命令处理
    ├── spawn <名称> → 创建新的 Steve
    └── <目标> <命令> → 发送给特定 Steve
```

## 核心设计决策

### 1. 静态状态管理

所有状态都是静态的，无需实例化：

```java
private static boolean isOpen = false;
private static float slideOffset = PANEL_WIDTH;
private static EditBox inputBox;
private static List<ChatMessage> messages = new ArrayList<>();
private static int scrollOffset = 0;
```

**原因**：GUI 是单例覆盖层，使用静态变量避免传递实例。

### 2. 动画系统

使用偏移量插值实现平滑滑动动画：

```java
if (isOpen && slideOffset > 0) {
    slideOffset = Math.max(0, slideOffset - ANIMATION_SPEED);
} else if (!isOpen && slideOffset < PANEL_WIDTH) {
    slideOffset = Math.min(PANEL_WIDTH, slideOffset + ANIMATION_SPEED);
}

int panelX = (int) (screenWidth - PANEL_WIDTH + slideOffset);
```

- `ANIMATION_SPEED = 20` 每帧像素数
- 面板从 `PANEL_WIDTH`（隐藏）滑动到 `0`（显示）
- 当 `slideOffset >= PANEL_WIDTH` 时跳过渲染

### 3. 消息气泡系统

三种不同样式的气泡：

| 类型 | 颜色 | 对齐方式 | 用途 |
|------|------|----------|------|
| 用户 | 绿色 (`0xC04CAF50`) | 右对齐 | 玩家命令 |
| Steve | 蓝色 (`0xC02196F3`) | 左对齐 | 智能体回复 |
| 系统 | 橙色 (`0xC0FF9800`) | 左对齐 | 状态消息 |

每个消息存储：
```java
private static class ChatMessage {
    String sender;      // 显示名称
    String text;        // 消息内容
    int bubbleColor;    // ARGB 颜色值
    boolean isUser;     // 对齐标志
}
```

### 4. 滚动实现

从底部向上渲染，带滚动偏移：

```java
// 从底部开始
int currentY = messageAreaBottom - 5;

for (int i = messages.size() - 1; i >= 0; i--) {
    int msgY = currentY - bubbleHeight + scrollOffset;

    // 跳过可见区域外的消息
    if (msgY + bubbleHeight < messageAreaTop - 20 || msgY > messageAreaBottom + 20) {
        currentY -= bubbleHeight + 5;
        continue;
    }

    // 渲染气泡...
    currentY -= bubbleHeight + 5 + 12; // 为发送者名称留出空间
}
```

- 消息从下往上渲染（最新消息在底部）
- `scrollOffset` 垂直偏移所有消息
- 裁剪区域防止溢出

### 5. 目标解析

智能命令路由到特定 Steve：

```java
// "all steves build a house" → 发送给所有人
// "Steve build a house" → 发送给 Steve
// "Steve, Alex build together" → 发送给两者
```

解析逻辑：
1. 检查 "all"、"everyone"、"everybody" 前缀
2. 按逗号分割实现多目标
3. 将第一个单词与可用 Steve 名称匹配
4. 如果没有匹配，默认发送给第一个可用的 Steve

## 渲染流程

```
RenderGuiOverlayEvent.Post
    │
    ├─ 更新动画偏移量
    │
    ├─ 如果隐藏则跳过 (slideOffset >= PANEL_WIDTH)
    │
    ├─ 启用混合模式
    │
    ├─ 绘制面板背景 (fillGradient)
    │   └─ 超透明: 0x15202020 (~8% 不透明度)
    │
    ├─ 绘制左侧边框
    │
    ├─ 绘制头部
    │   └─ "Steve AI" + "按 K 关闭"
    │
    ├─ 计算消息区域边界
    │   └─ 顶部: headerHeight + 5
    │   └─ 底部: screenHeight - 80
    │
    ├─ 启用裁剪 (限制在消息区域内)
    │
    ├─ 从下往上渲染消息
    │   ├─ 计算气泡尺寸
    │   ├─ 应用滚动偏移
    │   ├─ 跳过可见边界外的消息
    │   └─ 绘制气泡 + 发送者名称
    │
    ├─ 禁用裁剪
    │
    ├─ 绘制滚动条 (如果需要)
    │
    ├─ 绘制输入区域背景
    │
    ├─ 渲染 EditBox
    │
    └─ 绘制帮助文本
```

## 输入处理

### 按键绑定

| 按键 | 动作 |
|------|------|
| K | 切换面板 |
| ESC | 关闭面板 |
| Enter | 发送命令 |
| ↑ | 上一条命令 (历史) |
| ↓ | 下一条命令 (历史) |
| Backspace/Delete | 编辑输入 |
| Home/End | 导航输入框 |
| Left/Right | 移动光标 |

### 事件消费

面板打开时消费所有键盘事件：

```java
return true; // 输入时阻止游戏控制
```

## 集成接口

### 与 SteveMod 集成

```java
// 获取所有活跃的 Steve
var steves = SteveMod.getSteveManager().getAllSteves();

// 通过网络发送命令
mc.player.connection.sendCommand("steve tell " + steveName + " " + command);
```

### 与 SteveOverlayScreen 集成

创建屏幕实例用于输入焦点管理：

```java
if (isOpen) {
    mc.setScreen(new SteveOverlayScreen());
    inputBox.setFocused(true);
}
```

## 配置常量

| 常量 | 值 | 用途 |
|------|-----|------|
| `PANEL_WIDTH` | 200px | 面板宽度 |
| `PANEL_PADDING` | 6px | 内边距 |
| `ANIMATION_SPEED` | 20px/帧 | 滑动速度 |
| `MESSAGE_HEIGHT` | 12px | 文本行高 |
| `MAX_MESSAGES` | 500 | 历史记录限制 |
| `BACKGROUND_COLOR` | `0x15202020` | ~8% 不透明度黑色 |
| `BORDER_COLOR` | `0x40404040` | 25% 不透明度灰色 |
| `HEADER_COLOR` | `0x25252525` | ~15% 不透明度黑色 |

## 已知限制

1. **自动换行**：目前使用 "..." 截断，而非真正的换行
2. **文本格式**：不支持 markdown 或富文本
3. **消息持久化**：退出世界时消息丢失
4. **输入历史**：限制为 50 条命令
5. **性能**：每帧重新计算完整消息列表

## 使用示例

```
# 打开面板
按 K

# 生成新的 Steve
spawn Builder

# 给默认 Steve 发送命令
build a house here

# 给特定 Steve 发送命令
Steve dig a hole

# 给多个 Steve 发送命令
Steve, Alex gather wood

# 给所有 Steve 发送命令
all steves follow me
```
