# Steve AI - 我的世界自主AI智能体

为我的世界打造的Cursor。和帮助你写代码的AI不同，这是一个真正陪你玩游戏AI智能体。

https://github.com/user-attachments/assets/23f0ccdd-7a7a-4d49-9dd9-215ebf67265a

## 功能介绍

Steve作为一个智能体运行，你可以根据需要部署一个或多个智能体。你描述需求，Steve理解上下文并执行。同样的概念，只不过不是代码编辑，而是在你的我的世界游戏中运行的具身智能体。

界面很简单：按K打开面板，输入你的需求。智能体负责理解、规划和执行。说"挖点铁"，智能体会推理铁的生成位置、导航到合适的深度、找到矿石矿脉并提取资源。想要一座房子，它会考虑可用材料、生成合适的结构，然后一块一块地建造。

有趣的是多智能体协作。当多个Steve一起完成同一个任务时，他们不是独立执行，而是主动协调以避免冲突和优化工作分配。让三个智能体建造一座城堡，他们会自动划分结构、在他们之间分配区域、并行进行建造。

这些智能体不是按照预定义脚本运行的。它们根据自然语言指令操作，这意味着：
- **资源开采**：智能体确定最佳开采位置和策略
- **自主建造**：智能体规划布局和材料使用
- **战斗与防御**：智能体评估威胁并协调响应
- **探索与采集**：路径规划和资源定位
- **协作执行**：自动工作负载平衡和冲突解决

## 快速开始

**你需要：**
- Minecraft 1.20.1 含Forge
- Java 17
- OpenAI API密钥（或者如果你喜欢也可以用Groq/Gemini）

**安装步骤：**
1. 从 releases 下载JAR文件
2. 放入你的 `mods` 文件夹
3. 启动Minecraft
4. 将 `config/steve-common.toml.example` 复制为 `config/steve-common.toml`
5. 将你的API密钥添加到配置文件中

**配置示例：**
```toml
[openai]
apiKey = "your-api-key-here"
model = "gpt-3.5-turbo"
maxTokens = 1000
temperature = 0.7
```

然后用 `/steve spawn Bob` 生成一个Steve，按K开始下达指令。

## 使用示例

```
"挖20个铁矿石"
"在我附近建一座房子"
"帮助Alex建塔"
"保护我免受僵尸攻击"
"跟着我"
"从那片森林砍木头"
"在这里建一个圆石平台"
"攻击那个苦力怕"
```

智能体很擅长理解你的意思。不需要特别具体。

## 技术架构

### 系统概述

每个Steve运行一个自主智能体循环，通过LLM处理自然语言命令，将其转换为结构化动作，并使用我的世界的游戏机制执行。系统使用直接动作执行模型，针对实时游戏玩法进行了优化，而非传统的ReAct框架。

**核心执行流程：**
1. 通过GUI捕获用户输入（按K）
2. 将任务连同对话上下文发送到TaskPlanner
3. LLM（Groq/OpenAI/Gemini）生成结构化动作计划
4. ResponseParser从LLM响应中提取动作
5. ActionExecutor通过专业动作类处理动作
6. 动作逐tick执行以避免游戏冻结
7. 结果反馈到对话记忆中以提供上下文

### 核心组件

**LLM集成** (`com.steve.ai.llm`)
- **GeminiClient, GroqClient, OpenAIClient**：可插拔的LLM提供商用于智能体推理
- **TaskPlanner**：协调LLM调用，包含上下文（对话历史、世界状态、Steve能力）
- **PromptBuilder**：构建包含可用动作、示例和格式说明的提示
- **ResponseParser**：从LLM响应中提取结构化动作序列

**动作系统** (`com.steve.ai.action`)
- **ActionExecutor**：基于tick的动作执行引擎（防止游戏冻结）
- **BaseAction**：所有动作的抽象类（采矿、建造、移动、战斗等）
- **Task**：动作参数和元数据的数据模型
- **可用动作**：
  - MineBlockAction：智能矿石/方块开采与寻路
  - BuildStructureAction：程序化及模板化建造
  - PlaceBlockAction：单个方块放置与验证
  - MoveToAction：基于寻路的移动
  - AttackAction：目标选择的战斗
  - FollowAction：玩家/实体跟随
  - WaitAction：受控延迟和同步

**结构生成** (`com.steve.ai.structure`)
- **StructureGenerators**：程序化生成算法（房屋、城堡、塔楼、谷仓）
- **StructureTemplateLoader**：从资源加载NBT文件
- **BlockPlacement**：方块定位的共享数据结构

**多智能体协作** (`com.steve.ai.action`)
- **CollaborativeBuildManager**：并行建造的服务器端协调
- **空间分区**：自动将结构划分为非重叠区域
- **工作分配**：将区域分配给可用的Steve
- **冲突预防**：使用位置跟踪的原子方块放置
- **动态再平衡**：智能体提前完成时重新分配工作

**记忆与上下文** (`com.steve.ai.memory`)
- **SteveMemory**：每个智能体的对话历史和任务上下文
- **WorldKnowledge**：跟踪已发现的资源、地标和空间数据
- **StructureRegistry**：已建结构的目录以供参考和避让

**代码执行** (`com.steve.ai.execution`)
- **CodeExecutionEngine**：用于LLM生成脚本的GraalVM JavaScript引擎
- **SteveAPI**：将Minecraft动作暴露给脚本的安全API桥接
- **沙箱**：阻止有害操作的受限环境

### 关键设计决策

**基于Tick的执行**
动作在多个游戏tick中增量运行，而非阻塞。这防止了服务器冻结并保持响应性。每个动作的 `tick()` 方法每帧做最少的工作，并在内部跟踪进度。

**直接动作执行（非传统ReAct）**
虽然受ReAct启发，我们使用直接动作执行用于实时游戏。LLM预先生成完整的动作序列，而非迭代的观察-思考-动作循环。这减少了API调用和延迟，对游戏响应至关重要。

**多智能体协调**
协作建造使用确定性空间分区。结构根据智能体数量划分为矩形区域。每个Steve原子性地声明一个区域，防止冲突。管理器完全在服务器端使用ConcurrentHashMap实现线程安全。

**记忆管理**
上下文窗口通过修剪旧消息来管理，同时保留最近的对话和关键世界状态。每次LLM调用包括：对话历史（最近10次交换）、当前任务详情、Steve的位置/背包、以及已知的世界特征。

### 与Minecraft的集成

**实体注册**
Steve是通过Forge延迟注册系统注册的自定义EntityType。它们扩展PathfinderMob以集成 vanilla寻路，并实现自定义目标用于AI行为。

**事件钩子**
- ServerStarting：初始化协作建造管理器
- ServerStopping：清理活动任务并保存状态
- ClientTick：GUI渲染和输入处理

**GUI实现**
使用K键激活的自定义覆盖GUI。使用Minecraft的Screen类和自定义渲染。提交时将文本输入转发到TaskPlanner。

## 从源码构建

标准Gradle工作流程：

```bash
git clone https://github.com/YuvDwi/Steve.git
cd Steve
./gradlew build
```

输出JAR将在 `build/libs/`。要在开发环境测试：

```bash
./gradlew runClient
```

**项目结构：**
```
src/main/java/com/steve/ai/
├── entity/          # Steve实体、生成、生命周期
├── llm/             # LLM客户端、提示构建、响应解析
├── action/          # 动作类和协作建造管理器
├── structure/       # 程序化生成和模板加载
├── memory/          # 上下文管理和世界知识
├── execution/       # JavaScript代码执行引擎
├── client/          # GUI覆盖层
└── command/         # Minecraft命令（/steve spawn等）
```

## 贡献

欢迎贡献！以下是入门方法：

### 报告Bug

1. 首先检查[现有issue](https://github.com/YuvDwi/Steve/issues)
2. 包括：
   - Minecraft/Forge/Steve AI版本
   - 复现步骤
   - 预期与实际行为
   - 来自 `logs/latest.log` 的日志

### 提交代码

1. **Fork并克隆**
   ```bash
   git clone https://github.com/YourUsername/Steve.git
   cd Steve
   ```

2. **创建功能分支**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **进行更改**
   - 遵循代码风格（4空格缩进，公共API用JavaDoc）
   - 用 `./gradlew build && ./gradlew runClient` 测试

4. **提交PR**
   - 清晰的提交信息
   - 描述更改内容和原因
   - 关联相关issue

### 代码风格

- **类名**：PascalCase
- **方法/变量**：camelCase
- **常量**：UPPER_SNAKE_CASE
- **缩进**：4空格
- **行长度**：最多120字符
- **注释**：公共方法用JavaDoc

**添加新动作：**
1. 在 `com.steve.ai.action.actions` 中扩展 `BaseAction`
2. 实现 `tick()`、`isComplete()`、`onCancel()`
3. 更新 `PromptBuilder.java` 告知LLM新动作
4. 在提示模板中添加使用示例

## 配置

编辑 `config/steve-common.toml`：

```toml
[llm]
provider = "groq"  # 选项：openai, groq, gemini

[openai]
apiKey = "sk-..."
model = "gpt-3.5-turbo"
maxTokens = 1000
temperature = 0.7

[groq]
apiKey = "gsk_..."
model = "llama3-70b-8192"
maxTokens = 1000

[gemini]
apiKey = "AI..."
model = "gemini-1.5-flash"
maxTokens = 1000
```

**性能提示：**
- 使用Groq获得最快推理（推荐用于游戏）
- GPT-4更好的规划但延迟更高
- 较低温度（0.5-0.7）使动作更确定性

## 已知问题

**智能体只和LLM一样聪明。** GPT-3.5能用但偶尔会有奇怪的决定。GPT-4在多步规划上明显更好。

**还没有合成系统。** 智能体能挖矿和放置方块但还不能合成工具。正在努力开发中。

**动作是同步的。** 如果Steve正在挖矿，它在完成前什么都做不了。计划添加正确的异步执行。

**记忆在重启时重置。** 目前上下文只在游戏会话期间持续。正在添加带有向量数据库的持久化记忆。

## 后续计划

计划中的功能：
- 合成系统（智能体自己制作工具）
- 通过Whisper API实现语音命令
- 用于长期记忆的向量数据库
- 用于多任务的异步动作执行
- 更多建造模板和程序化生成
- 复杂地形的增强寻路

目标是让这个在生存游戏中真正有用，而不仅仅是一个技术演示。

## 为什么做这个

我们想看看Cursor模型是否能在编码之外工作。事实证明它相当适用。同样的原则：深度环境集成、清晰的动作原语、持久上下文。

我的世界实际上是智能体研究的好测试场。足够复杂以至于有趣，足够受限以至于智能体能够真正成功。

而且看着AI建造城堡而你去探索真的很有趣。

## 致谢

- OpenAI/Groq/Google提供LLM API
- Minecraft Forge提供模组框架
- LangChain/AutoGPT提供智能体架构灵感

## 许可证

MIT

## 问题反馈

发现Bug？开一个issue：https://github.com/YuvDwi/Steve/issues
