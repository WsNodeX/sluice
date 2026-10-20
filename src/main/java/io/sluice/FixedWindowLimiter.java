package io.sluice;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 固定窗口（Fixed Window）。
 *
 * <p>把时间切成等长窗口，每个窗口内最多放行 {@code maxPerWindow} 个许可，
 * 窗口翻转时计数清零。O(1) 内存、实现最简单，代价是窗口边界处可能通过
 * 至多 2 × max 的突发（前一个窗口的尾巴 + 新窗口的开头）。
 *
 * <p>适合对精度要求不高、但要省内存的粗粒度限流，例如每分钟最多
 * 调用多少次外部接口。
 */
public final class FixedWindowLimiter implements RateLimiter {

    private final long windowNanos;
    private final int maxPerWindow;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private long windowStart;
    private int used;

    FixedWindowLimiter(long windowNanos, int maxPerWindow, Clock clock) {
        if (windowNanos <= 0) {
            throw new IllegalArgumentException("window must be > 0");
        }
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException("maxPerWindow must be > 0, got " + maxPerWindow);
        }
        this.windowNanos = windowNanos;
        this.maxPerWindow = maxPerWindow;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.windowStart = clock.nanoTime();
        this.used = 0;
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0, got " + permits);
        }
        if (permits > maxPerWindow) {
            return false;               // 单次请求超过整个窗口额度，永远不可能满足
        }
        lock.lock();
        try {
            long now = clock.nanoTime();
            if (now - windowStart >= windowNanos) {
                windowStart = now;      // 翻到"当前"这个新窗口
                used = 0;
            }
            if (used + permits <= maxPerWindow) {
                used += permits;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}
