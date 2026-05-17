# 配置参考

## 配置文件

`config/steve-common.toml`

## LLM 配置

```toml
[llm]
provider = "groq"  # 或 "openai", "gemini"
```

### OpenAI

```toml
[openai]
apiKey = "your-api-key"
model = "gpt-3.5-turbo"
maxTokens = 1000
temperature = 0.7
```

### Groq

```toml
[groq]
apiKey = "your-api-key"
model = "llama-3.1-70b"
```

### Gemini

```toml
[gemini]
apiKey = "your-api-key"
model = "gemini-pro"
```

## 行为配置

```toml
[behavior]
actionTickDelay = 20      # 动作检查间隔 (tick)
enableChatResponses = true
maxActiveSteves = 10      # 最大活跃 Steve 数量
```

## 技术栈

- **Minecraft Forge**: 1.20.1-47.2.0
- **GraalVM Polyglot**: JavaScript 代码执行
- **Resilience4j**: 熔断器、重试、限流、隔舱模式
- **Caffeine**: LLM 响应缓存
- **Commons Codec**: SHA-256 哈希（缓存键）
