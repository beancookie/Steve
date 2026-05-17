# 弹性系统 - LLM 调用容错机制

## 概述

弹性系统使用 Resilience4j 库为 LLM 调用提供容错保护，包括电路断路器、重试机制、速率限制、隔舱模式和响应缓存。确保在 API 不可用或响应缓慢时系统仍能正常运行。

## 架构设计

```
弹性系统
├── ResilientLLMClient (装饰器)
│   ├── 原始 LLM 客户端（委托对象）
│   ├── LLMCache (响应缓存)
│   ├── LLMFallbackHandler (降级处理)
│   └── Resilience4j 组件
│       ├── CircuitBreaker (电路断路器)
│       ├── Retry (重试机制)
│       ├── RateLimiter (速率限制)
│       └── Bulkhead (隔舱模式)
├── 请求流程
│   1. 检查缓存 → 命中则返回
│   2. 检查速率限制 → 超限则等待/拒绝
│   3. 检查隔舱 → 满则等待/拒绝
│   4. 检查电路断路器 → 开启则降级
│   5. 执行请求（带重试）
│   6. 成功 → 缓存响应，返回
│   7. 失败 → 触发降级处理
└── ResilienceConfig (配置)
    ├── 电路断路器参数
    ├── 重试参数
    ├── 速率限制参数
    └── 隔舱参数
```

## 核心设计决策

### 1. 装饰器模式

在不修改原始客户端的情况下添加弹性功能：

```java
// 原始客户端
AsyncLLMClient rawClient = new AsyncOpenAIClient(apiKey, model, maxTokens, temp);

// 添加弹性保护
AsyncLLMClient resilientClient = new ResilientLLMClient(rawClient, cache, fallback);

// 所有调用都受保护
resilientClient.sendAsync("Build a house", params)
    .thenAccept(response -> processResponse(response));
```

**优势**：
- 单一职责（每个组件只负责一个弹性功能）
- 可组合（可以混合搭配不同组件）
- 易于测试（可以 mock 任何组件）

### 2. 请求流程

```java
public CompletableFuture<LLMResponse> sendAsync(String prompt, Map<String, Object> params) {
    return CompletableFuture.supplyAsync(() -> {
        // 1. 检查缓存
        String cacheKey = generateCacheKey(prompt, params);
        Optional<LLMResponse> cached = cache.get(cacheKey);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2. 检查速率限制
        if (!rateLimiter.acquirePermission()) {
            throw new LLMException("Rate limit exceeded");
        }

        // 3. 检查隔舱
        if (!bulkhead.tryAcquirePermission()) {
            throw new LLMException("Bulkhead full");
        }

        try {
            // 4. 检查电路断路器（由 Resilience4j 自动处理）
            // 5. 执行请求（带重试）
            LLMResponse response = executeWithRetry(prompt, params);

            // 6. 缓存响应
            cache.put(cacheKey, response);

            return response;
        } finally {
            bulkhead.releasePermission();
        }
    }).exceptionally(throwable -> {
        // 7. 降级处理
        return fallbackHandler.handleFallback(prompt, params, throwable);
    });
}
```

### 3. 电路断路器（Circuit Breaker）

防止持续调用失败的服务：

```java
CircuitBreakerConfig config = CircuitBreakerConfig.custom()
    .failureRateThreshold(50)           // 失败率阈值 50%
    .waitDurationInOpenState(Duration.ofSeconds(30))  // 开启状态等待 30 秒
    .slidingWindowSize(10)              // 滑动窗口大小 10
    .minimumNumberOfCalls(5)            // 最少调用次数 5
    .build();

CircuitBreaker circuitBreaker = CircuitBreaker.of("llm", config);
```

**状态转换**：
```
关闭 (CLOSED)
    │
    ├─ 失败率 < 50% → 保持关闭
    │
    └─ 失败率 ≥ 50% → 开启 (OPEN)
        │
        ├─ 等待 30 秒
        │
        └─ 半开 (HALF_OPEN)
            │
            ├─ 调用成功 → 关闭
            │
            └─ 调用失败 → 开启
```

### 4. 重试机制（Retry）

自动重试失败的请求：

```java
RetryConfig config = RetryConfig.custom()
    .maxAttempts(3)                     // 最大重试次数 3
    .waitDuration(Duration.ofSeconds(1)) // 等待时间 1 秒
    .retryExceptions(                    // 可重试的异常
        IOException.class,
        TimeoutException.class,
        LLMException.class
    )
    .ignoreExceptions(                   // 忽略的异常
        IllegalArgumentException.class
    )
    .build();

Retry retry = Retry.of("llm", config);
```

**重试策略**：
```java
// 指数退避
RetryConfig.custom()
    .intervalFunction(IntervalFunction.ofExponentialBackoff(
        1000,  // 初始间隔 1 秒
        2.0    // 倍数
    ))
    .build();

// 结果：1s, 2s, 4s, 8s...
```

### 5. 速率限制（Rate Limiter）

防止 API 配额耗尽：

```java
RateLimiterConfig config = RateLimiterConfig.custom()
    .limitForPeriod(10)                 // 每个周期 10 次
    .limitRefreshPeriod(Duration.ofSeconds(1))  // 刷新周期 1 秒
    .timeoutDuration(Duration.ofSeconds(5))     // 等待超时 5 秒
    .build();

RateLimiter rateLimiter = RateLimiter.of("llm", config);
```

**使用场景**：
- OpenAI：3 RPM（免费）、60 RPM（付费）
- Groq：30 RPM
- Gemini：60 RPM

### 6. 隔舱模式（Bulkhead）

限制并发请求数量：

```java
BulkheadConfig config = BulkheadConfig.custom()
    .maxConcurrentCalls(5)              // 最大并发 5
    .maxWaitDuration(Duration.ofSeconds(10))  // 等待超时 10 秒
    .build();

Bulkhead bulkhead = Bulkhead.of("llm", config);
```

**作用**：
- 防止线程池耗尽
- 限制资源使用
- 快速失败

### 7. 响应缓存

减少重复请求：

```java
public class LLMCache {
    private final Cache<String, LLMResponse> cache;

    public LLMCache() {
        this.cache = Caffeine.newBuilder()
            .maximumSize(1000)              // 最大条目 1000
            .expireAfterWrite(5, TimeUnit.MINUTES)  // 写入后 5 分钟过期
            .build();
    }

    public Optional<LLMResponse> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public void put(String key, LLMResponse response) {
        cache.put(key, response);
    }
}
```

**缓存键生成**：
```java
private String generateCacheKey(String prompt, Map<String, Object> params) {
    String input = prompt + "|" + params.toString();
    return DigestUtils.sha256Hex(input);  // SHA-256 哈希
}
```

### 8. 降级处理（Fallback）

所有弹性措施都失败时的兜底方案：

```java
public class LLMFallbackHandler {
    public LLMResponse handleFallback(String prompt, Map<String, Object> params, Throwable throwable) {
        LOGGER.warn("LLM call failed, using fallback: {}", throwable.getMessage());

        // 基于模式的降级响应
        if (prompt.contains("build")) {
            return new LLMResponse("{\"action\": \"build\", \"structure\": \"house\"}");
        } else if (prompt.contains("mine")) {
            return new LLMResponse("{\"action\": \"mine\", \"block\": \"iron\", \"quantity\": 8}");
        }

        // 默认降级：跟随玩家
        return new LLMResponse("{\"action\": \"follow\", \"player\": \"nearest\"}");
    }
}
```

## 配置

### ResilienceConfig

```java
public class ResilienceConfig {
    // 电路断路器
    public static final float FAILURE_RATE_THRESHOLD = 50.0f;
    public static final int WAIT_DURATION_SECONDS = 30;
    public static final int SLIDING_WINDOW_SIZE = 10;
    public static final int MIN_CALLS = 5;

    // 重试
    public static final int MAX_RETRIES = 3;
    public static final long RETRY_DELAY_MS = 1000;
    public static final double RETRY_MULTIPLIER = 2.0;

    // 速率限制
    public static final int RATE_LIMIT_PER_SECOND = 10;
    public static final long RATE_LIMIT_TIMEOUT_MS = 5000;

    // 隔舱
    public static final int MAX_CONCURRENT_CALLS = 5;
    public static final long BULKHEAD_TIMEOUT_MS = 10000;

    // 缓存
    public static final int CACHE_MAX_SIZE = 1000;
    public static final long CACHE_EXPIRE_MINUTES = 5;
}
```

### 配置文件

```toml
# config/steve-common.toml

[resilience]
    [resilience.circuitBreaker]
        failureRateThreshold = 50.0
        waitDurationSeconds = 30
        slidingWindowSize = 10
        minimumNumberOfCalls = 5

    [resilience.retry]
        maxAttempts = 3
        waitDurationMs = 1000
        multiplier = 2.0

    [resilience.rateLimiter]
        limitForPeriod = 10
        limitRefreshPeriodMs = 1000
        timeoutDurationMs = 5000

    [resilience.bulkhead]
        maxConcurrentCalls = 5
        maxWaitDurationMs = 10000

    [resilience.cache]
        maxSize = 1000
        expireAfterMinutes = 5
```

## 使用示例

### 基本使用

```java
// 创建客户端
AsyncLLMClient rawClient = new AsyncGroqClient(apiKey, model);
LLMCache cache = new LLMCache();
LLMFallbackHandler fallback = new LLMFallbackHandler();

AsyncLLMClient client = new ResilientLLMClient(rawClient, cache, fallback);

// 发送请求（自动受保护）
client.sendAsync("Build a castle", params)
    .thenAccept(response -> {
        if (response.isSuccess()) {
            processResponse(response);
        } else {
            handleFallback(response);
        }
    })
    .exceptionally(throwable -> {
        LOGGER.error("Request failed: {}", throwable.getMessage());
        return null;
    });
```

### 监控弹性指标

```java
// 电路断路器指标
CircuitBreaker.Metrics cbMetrics = circuitBreaker.getMetrics();
int failureRate = cbMetrics.getFailureRate();
int successfulCalls = cbMetrics.getNumberOfSuccessfulCalls();
int failedCalls = cbMetrics.getNumberOfFailedCalls();

// 速率限制指标
RateLimiter.Metrics rlMetrics = rateLimiter.getMetrics();
int availablePermissions = rlMetrics.getAvailablePermissions();
int waitingThreads = rlMetrics.getNumberOfWaitingThreads();

// 隔舱指标
Bulkhead.Metrics bhMetrics = bulkhead.getMetrics();
int availableConcurrentCalls = bhMetrics.getAvailableConcurrentCalls();
int maxAllowedConcurrentCalls = bhMetrics.getMaxAllowedConcurrentCalls();
```

### 动态配置

```java
// 运行时修改配置
circuitBreaker.transitionToOpenState();
circuitBreaker.transitionToHalfOpenState();
circuitBreaker.transitionToDisabledState();

// 重置指标
circuitBreaker.reset();
rateLimiter.resetMetrics();
```

## 性能影响

### 缓存命中

- 缓存命中：~1μs（内存查找）
- 缓存未命中：~500ms-30s（LLM 调用）
- 命中率：40-60%（典型场景）

### 弹性开销

- 电路断路器：~100ns（状态检查）
- 重试机制：~1μs（检查重试条件）
- 速率限制：~200ns（令牌桶检查）
- 隔舱模式：~300ns（信号量获取）

**总开销**：< 1μs（可忽略）

## 错误处理

### 可重试错误

```java
.retryExceptions(
    IOException.class,          // 网络错误
    TimeoutException.class,     // 超时
    LLMException.class          // LLM 特定错误
)
```

### 不可重试错误

```java
.ignoreExceptions(
    IllegalArgumentException.class,  // 参数错误
    AuthenticationException.class    // 认证失败
)
```

### 降级触发条件

1. 电路断路器开启
2. 速率限制超限
3. 隔舱满
4. 重试次数用尽
5. 不可重试异常

## 监控和日志

### 日志配置

```properties
# log4j2.properties
logger.resilience.name = com.steve.ai.llm.resilience
logger.resilience.level = DEBUG

logger.resilience4j.name = io.github.resilience4j
logger.resilience4j.level = INFO
```

### 日志输出

```
[DEBUG] Circuit breaker state: CLOSED
[DEBUG] Rate limiter: 8/10 permissions available
[DEBUG] Bulkhead: 3/5 concurrent calls
[INFO] Cache hit: key=abc123
[WARN] Circuit breaker OPEN: 50% failure rate
[INFO] Retry attempt 2/3 after 1000ms
[ERROR] All retries exhausted, using fallback
```

## 最佳实践

1. **合理配置**：根据 API 限制配置速率限制
2. **监控指标**：定期检查电路断路器状态
3. **降级策略**：设计有意义的降级响应
4. **缓存策略**：根据数据新鲜度需求设置过期时间
5. **日志记录**：记录所有弹性事件用于调试

## 已知限制

1. **单节点**：不支持分布式弹性（如 Redis 缓存）
2. **静态配置**：配置变更需重启
3. **无优先级队列**：无法优先处理重要请求
4. **无请求合并**：相同请求不会合并

## 扩展建议

1. **分布式缓存**：使用 Redis 替代本地缓存
2. **动态配置**：支持运行时配置更新
3. **请求优先级**：支持优先级队列
4. **请求合并**：相同请求自动合并
5. **指标导出**：导出到 Prometheus/Grafana
