package io.sluice;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 滑动计数（Sliding Window Counter，双桶加权）。
 *
 * <p>固定窗口的边界突发问题 + 滑动日志的内存开销，折中出这一种：
 * 只保留「上一个窗口」和「当前窗口」两个计数器，估算当前时刻的窗口用量时，
 * 把上一个窗口的计数按时间流逝比例折减：
 *
 * <pre>  估算用量 = prev × (1 - 已流逝比例) + curr</pre>
 *
 * <p>O(1) 内存，精度远好于固定窗口。估算值有小概率轻微偏保守
 * （多拒了 1 个请求），属于该算法的固有取舍。
 */
public final class SlidingWindowCounter implements RateLimiter {

    private final long windowNanos;
    private final int maxPerWindow;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private long currentWindowStart;
    private int prevCount;
    private int currCount;

    SlidingWindowCounter(long windowNanos, int maxPerWindow, Clock clock) {
        if (windowNanos <= 0) {
            throw new IllegalArgumentException("window must be > 0");
        }
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException("maxPerWindow must be > 0, got " + maxPerWindow);
        }
        this.windowNanos = windowNanos;
        this.maxPerWindow = maxPerWindow;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.currentWindowStart = clock.nanoTime();
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0, got " + permits);
        }
        lock.lock();
        try {
            long now = clock.nanoTime();
            rollWindows(now);
            double elapsedRatio = (now - currentWindowStart) / (double) windowNanos;
            double estimated = prevCount * (1.0 - elapsedRatio) + currCount;
            if (estimated + permits <= maxPerWindow) {
                currCount += permits;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 把窗口状态推进到 now 所处的窗口（跨多个窗口则全部清零）。 */
    private void rollWindows(long now) {
        long elapsed = now - currentWindowStart;
        if (elapsed < windowNanos) {
            return;
        }
        long windowsPassed = elapsed / windowNanos;
        if (windowsPassed >= 2) {
            prevCount = 0;              // 中间隔了完整窗口，上一窗口的量完全过期
        } else {
            prevCount = currCount;
        }
        currCount = 0;
        currentWindowStart += windowsPassed * windowNanos;
    }
}
