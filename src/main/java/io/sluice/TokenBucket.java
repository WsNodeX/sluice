package io.sluice;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 令牌桶（Token Bucket）。
 *
 * <p>桶的容量即瞬时突发上限：满桶时可以一口气放行 {@code capacity} 个请求，
 * 随后以 {@code refillPerSecond} 的速率匀速补充。这是四种算法里唯一同时
 * 兼顾「平均速率」与「可控突发」的，也是网关限流最常见的默认选择。
 *
 * <p>实现要点：
 * <ul>
 *   <li>令牌量用 double 连续累积，补充量 = 流过时间 × 速率，避免整数化误差；</li>
 *   <li>只在 {@code tryAcquire} 时惰性补充，没有后台线程，零额外开销；</li>
 *   <li>{@link ReentrantLock} 串行化状态变更——临界区只有几条浮点运算，
 *       在真实负载下远未成为瓶颈。</li>
 * </ul>
 */
public final class TokenBucket implements RateLimiter {

    private final double capacity;
    private final double refillPerNano;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private double tokens;
    private long lastRefill;

    TokenBucket(double capacity, double refillPerSecond, Clock clock) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0, got " + capacity);
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be > 0, got " + refillPerSecond);
        }
        this.capacity = capacity;
        this.refillPerNano = refillPerSecond / 1_000_000_000.0;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tokens = capacity;          // 满桶起步：服务冷启动允许一个完整突发
        this.lastRefill = clock.nanoTime();
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0, got " + permits);
        }
        lock.lock();
        try {
            long now = clock.nanoTime();
            double elapsed = now - lastRefill;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerNano);
                lastRefill = now;
            }
            if (tokens >= permits) {
                tokens -= permits;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 当前桶内令牌数（触发一次惰性补充后的快照，供监控/测试观察）。 */
    public double availableTokens() {
        lock.lock();
        try {
            long now = clock.nanoTime();
            double elapsed = now - lastRefill;
            if (elapsed > 0) {
                return Math.min(capacity, tokens + elapsed * refillPerNano);
            }
            return tokens;
        } finally {
            lock.unlock();
        }
    }
}
