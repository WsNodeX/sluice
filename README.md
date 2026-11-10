<div align="center">

# SLUICE

### 零依赖 Java 限流库 · 四种经典算法，一个 API

*令牌桶 / 固定窗口 / 滑动日志 / 滑动计数 —— 线程安全、确定性可测、不拖一个第三方依赖。*

[![Stars](https://img.shields.io/github/stars/WsNodeX/sluice?style=flat-square&color=111111&label=stars)](https://github.com/WsNodeX/sluice/stargazers)
[![Release](https://img.shields.io/github/v/release/WsNodeX/sluice?style=flat-square&color=111111&label=release)](https://github.com/WsNodeX/sluice/releases)
[![License](https://img.shields.io/badge/license-MIT-111111?style=flat-square)](LICENSE)

</div>

---

每个后端系统迟早都会遇到同一个问题：**调用方不守规矩怎么办？**

限流的答案满天飞，但要么拖着一个重量级框架，要么只实现单一算法，要么把
`System.currentTimeMillis()` 写死——单元测试只能靠 `Thread.sleep()` 碰运气。

Sluice 把四种经典算法装进一个 20 KB 的纯 Java 库：

- **零依赖**：只有 JDK，`javac` 直接编译，塞进任何项目都不带行李。
- **时间可注入**：所有算法基于 `Clock` 接口，测试里拨动假时钟，毫秒级行为完全确定。
- **线程安全**：并发测试验证「400 次抢购恰好放行 100 个」，绝不超卖。

## 快速开始

```bash
git clone https://github.com/WsNodeX/sluice.git
cd sluice
javac --release 17 -d build $(find src/main/java -name '*.java')
```

```java
import sluice.Sluice;
import sluice.RateLimiter;

// 容量 100、每秒补 10 个令牌
RateLimiter limiter = Sluice.tokenBucket()
        .capacity(100)
        .refillPerSecond(10)
        .build();

if (limiter.tryAcquire()) {
    // 放行
} else {
    // 限流：返回 429，或降级
}
```

换算法只改一行构建代码：

```java
RateLimiter limiter = Sluice.slidingWindowCounter()
        .window(Duration.ofSeconds(1))
        .maxPerWindow(100)
        .build();
```

## 四种算法怎么选

| 算法 | 精度 | 内存 | 突发行为 | 适用场景 |
|:---|:---:|:---:|:---|:---|
| 令牌桶 | 高 | O(1) | 允许受控突发 | API 网关、开放平台默认选择 |
| 固定窗口 | 低（边界 2× 突发） | O(1) | 边界处可能双倍 | 粗粒度配额：每分钟调 X 次外部接口 |
| 滑动日志 | 精确 | O(max) | 无 | 限额小、要求严格：支付回调 |
| 滑动计数 | 近似（偏保守） | O(1) | 基本消除 | 大流量下滑动日志的省内存替代 |

## 架构

```mermaid
graph TD
  R[调用方] -->|tryAcquire / tryAcquire(n)| I[RateLimiter 接口<br/>非阻塞·原子批量·不超卖]
  I --> TB[TokenBucket<br/>突发友好]
  I --> FW[FixedWindow<br/>最省内存]
  I --> SWL[SlidingWindowLog<br/>精确]
  I --> SWC[SlidingWindowCounter<br/>O 1 内存近似]
  TB --> C[Clock 接口<br/>唯一时间来源]
  FW --> C
  SWL --> C
  SWC --> C
  C -.->|测试注入假时钟| T[确定性推演]
```

`Clock` 是唯一的时间来源，是整个库可测性的基石。生产用系统时钟，测试传一个可以
任意拨动的假时钟，窗口滚动与令牌补充就能被精确推演，不依赖 `sleep`。

## API

| 方法 | 说明 |
|---|---|
| `RateLimiter.tryAcquire()` | 尝试获取 1 个许可，非阻塞，立即返回 |
| `RateLimiter.tryAcquire(int permits)` | 原子获取 n 个：全部成功或全部失败，无中间态 |
| `Sluice.tokenBucket()` | `.capacity(n)` `.refillPerSecond(rate)` |
| `Sluice.fixedWindow()` | `.window(Duration)` `.maxPerWindow(n)` |
| `Sluice.slidingWindowLog()` | `.window(Duration)` `.maxPerWindow(n)` |
| `Sluice.slidingWindowCounter()` | `.window(Duration)` `.maxPerWindow(n)` |
| `*.clock(Clock)` | 注入自定义时间源 |
| `TokenBucket.availableTokens()` | 当前令牌快照（监控用） |
| `SlidingWindowLog.currentWindowCount()` | 当前窗口已用量（监控用） |

## 测试

```bash
# 需要 JDK 17+ 与 junit-platform-console-standalone
java -jar junit-platform-console-standalone-1.10.2.jar \
     --class-path build --scan-class-path
```

29 个 JUnit 5 用例，全部基于假时钟，包括一个 400 线程抢 100 许可的并发测试。

## 限制

- **单机限流。** 状态在进程内存里，多实例部署时各自计数，总量不共享。要全局配额得
  自己接 Redis。
- **没有阻塞式 `acquire()`。** 只有非阻塞的 `tryAcquire()`，调用方自己决定拒绝后做什么。
- **滑动计数是近似值。** 它偏保守（宁可多拒），要精确就用滑动日志，代价是 O(max) 内存。

## 路线图

- [ ] 阻塞式 `acquire()`（等到许可可用）
- [ ] 限流结果携带 `retryAfter` 建议
- [ ] 分布式扩展点（适配 Redis 等共享存储的接口预留）
- [ ] 注解 + AOP 集成示例

## 许可

[MIT](LICENSE)
