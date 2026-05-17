# LLM 集成

## 支持的提供商

| 提供商 | 模型 | 特点 |
|--------|------|------|
| OpenAI | GPT-3.5-turbo | 通用能力强 |
| Groq | llama-3.1-70b | 低延迟 |
| Gemini | gemini-pro | Google 生态 |

## 核心组件

- `TaskPlanner.java` - LLM 调用编排
- `PromptBuilder.java` - 构建提示词
- `ResponseParser.java` - 解析 LLM 响应
- `OpenAIClient.java`, `GroqClient.java`, `GeminiClient.java` - 各提供商客户端

## 关键特性

### 1. 异步非阻塞调用
使用 `CompletableFuture` 确保游戏线程永远不被 LLM 调用阻塞。

### 2. 缓存
- 使用 Caffeine 缓存
- 40-60% 缓存命中率
- SHA-256 哈希作为缓存键

### 3. 熔断器模式
- 使用 Resilience4j
- 主提供商失败时自动切换到 Groq
- 支持重试、限流、隔舱模式

## 配置

`config/steve-common.toml`:

```toml
[llm]
provider = "groq"

[openai]
apiKey = "your-key"
model = "gpt-3.5-turbo"
maxTokens = 1000
temperature = 0.7
```
