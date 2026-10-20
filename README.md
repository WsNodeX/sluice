<div align="center">

# SLUICE

### 零依赖 Java 限流库 · 四种经典算法，一个 API

*令牌桶 / 固定窗口 / 滑动日志 / 滑动计数 —— 线程安全、确定性可测、不拖一个第三方依赖。*

[![Java](https://img.shields.io/badge/java-17%2B-orange.svg)](https://openjdk.org)
[![Deps](https://img.shields.io/badge/dependencies-zero-00e5c0.svg)](#特性)
[![Tests](https://img.shields.io/badge/tests-junit%205%20%C3%97%2029-brightgreen.svg)](#运行测试)
[![Concurrency](https://img.shields.io/badge/thread--safe-verified-blue.svg)](#特性)
[![License: MIT](https://img.shields.io/badge/license-MIT-yellow.svg)](LICENSE)

[为什么](#-为什么) · [特性](#-特性) · [快速开始](#-快速开始) · [架构](#️-架构) · [算法选型](#-算法选型) · [API](#-api) · [路线图](#-路线图) · [许可](#-许可)

</div>

---

## ✨ 为什么

每个后端系统迟早都会遇到同一个问题：**调用方不守规矩怎么办？**

限流的答案满天飞，但要么拖着一个重量级框架，要么只实现了单一算法，要么拿 `System.currentTimeMillis()` 写死了时间——单元测试只能靠 `Thread.sleep()` 碰运气。Sluice 把四种种经典算法装进一个 20 KB 的纯 Java 库：

- **零依赖**：只有 JDK，`javac` 直接编译，塞进任何项目都不带行李；
- **时间可注入**：所有算法基于 `Clock` 接口，测试里拨动假时钟，毫秒级行为完全确定；
- **线程安全**：并发测试验证「400 次抢购恰好放行 100 个」，绝不超卖。

## 🚀 特性

- **四种算法开箱即用** —— 令牌桶（突发友好）、固定窗口（最省内存）、滑动日志（精确无边界突发）、滑动计数（O(1) 内存的近似滑动）。
- **统一 API** —— 一个 `RateLimiter` 接口，换算法只改一行构建代码。
- **Builder 门面** —— `Sluice.tokenBucket().capacity(100).refillPerSecond(10).build()`，配置即时校验。
- **原子批量获取** —— `tryAcquire(n)` 要么全部成功要么全部失败，不存在拿走一半的中间态。
- **惰性补充，无后台线程** —— 令牌只在请求到达时按流逝时间折算补充，零调度开销。
- **确定性测试** —— 假时钟精确推演任意时刻的窗口滚动与令牌补充，29 个 JUnit 测试全绿。

## ⚡ 快速开始

**编译：**

```bash
git clone https://github.com/WsNodeX/sluice.git
cd sluice
javac --release 17 -d build $(find src/main/java -name '*.java')
```

**跑演示（容量 3、每秒补 10 的令牌桶 vs 25 QPS 的请求流）：**

```bash
java -cp build io.sluice.Demo
```

预期输出：

```text
req 01 -> PASS    (available=2.00)
req 02 -> PASS    (available=1.64)
req 03 -> PASS    (available=1.10)
req 04 -> PASS    (available=0.56)
req 05 -> PASS    (available=0.02)
req 06 -> REJECT  (available=0.48)
req 07 -> REJECT  (available=0.95)
req 08 -> PASS    (available=0.41)
...
total: 9 passed, 6 rejected
```

突发额度被瞬间吃光，之后严格按 10 个/秒的速率恢复放行——这就是令牌桶的节奏。

**在自己代码里使用：**

```java
RateLimiter limiter = Sluice.tokenBucket()
        .capacity(100)
        .refillPerSecond(10)
        .build();

if (limiter.tryAcquire()) {
    // 放行
} else {
    // 返回 429 Too Many Requests
}
```

## 运行测试

```bash
# 需要 JDK 17+ 与 junit-platform-console-standalone-1.10.2.jar
javac --release 17 -d build $(find src/main/java -name '*.java')
javac --release 17 -cp "build:junit.jar" -d build/test $(find src/test/java -name '*.java')
java -jar junit.jar execute -cp "build:test" --scan-class-path
```

## 🏗️ 架构

```
        ┌──────────────────────────────────────────────┐
        │                 Sluice (门面)                │  Builder + 默认值 + 校验
        └──────┬───────────┬───────────┬───────────────┘
               │           │           │
     ┌─────────▼──┐ ┌──────▼──────┐ ┌─▼──────────────┐ ┌────────────────┐
     │ TokenBucket│ │ FixedWindow │ │SlidingWindowLog│ │SlidingWindow-  │
     │            │ │             │ │                │ │    Counter     │
     └─────┬──────┘ └──────┬──────┘ └───────┬────────┘ └───────┬────────┘
           └───────────────┴───────┬────────┴──────────────────┘
                                   │
                        ┌──────────▼──────────┐
                        │  RateLimiter 接口   │  tryAcquire() / tryAcquire(n)
                        └──────────┬──────────┘
                                   │
                        ┌──────────▼──────────┐
                        │     Clock 接口      │  时间注入：生产用系统时钟，
                        └─────────────────────┘  测试用可拨动的假时钟
```

- **算法层**：各实现只管自己的状态与判定，互不感知；
- **抽象层**：`RateLimiter` 约束「非阻塞、原子批量、不超卖」的统一契约；
- **时钟层**：`Clock` 是唯一的时间来源，是整个库可测性的基石。

## 📐 算法选型

| 算法 | 精度 | 内存 | 突发 | 适用场景 |
|:---|:---:|:---:|:---|:---|
| 令牌桶 | 高 | O(1) | 允许受控突发 | API 网关、开放平台默认选择 |
| 固定窗口 | 低（边界 2× 突发） | O(1) | 边界处可能双倍 | 粗粒度配额：每分钟调 X 次外部接口 |
| 滑动日志 | 精确 | O(max) | 无 | 限额小、要求严格：支付回调 |
| 滑动计数 | 近似（偏保守） | O(1) | 基本消除 | 大流量下滑动日志的省内存替代 |

## 🔌 API

| 方法 | 说明 |
|:---|:---|
| `RateLimiter.tryAcquire()` | 尝试获取 1 个许可，非阻塞，立即返回 |
| `RateLimiter.tryAcquire(int permits)` | 原子获取 n 个许可：全部成功或全部失败 |
| `Sluice.tokenBucket()` | 令牌桶：`.capacity(n)` `.refillPerSecond(rate)` |
| `Sluice.fixedWindow()` | 固定窗口：`.window(Duration)` `.maxPerWindow(n)` |
| `Sluice.slidingWindowLog()` | 滑动日志：`.window(Duration)` `.maxPerWindow(n)` |
| `Sluice.slidingWindowCounter()` | 滑动计数：`.window(Duration)` `.maxPerWindow(n)` |
| `*.clock(Clock)` | 注入自定义时间源（测试时传可拨动的假时钟） |
| `TokenBucket.availableTokens()` | 当前令牌快照（监控用） |
| `SlidingWindowLog.currentWindowCount()` | 当前窗口已用量（监控用） |

## 🧭 路线图

- [x] 四种经典算法核心实现
- [x] Builder 门面 + 即时参数校验
- [x] 可注入时钟 + 确定性测试
- [x] 并发安全验证
- [ ] 等待型 `acquire()`（阻塞直至许可可用）
- [ ] 限流结果携带 `retryAfter` 等待时长建议
- [ ] 分布式扩展点（适配 Redis 等共享存储的接口预留）
- [ ] 注解 + AOP 集成示例

## 🤝 贡献

Issue 与 PR 都欢迎。提交前请确保 `mvn`/`javac` 编译通过、全部测试绿灯；新算法请附上基于假时钟的确定性测试与并发测试。

## 📄 许可

[MIT](LICENSE) —— 自由使用、修改、再分发。
