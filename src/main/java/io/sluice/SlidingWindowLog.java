package io.sluice;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 滑动日志（Sliding Window Log）。
 *
 * <p>为每个拿到的许可记一条时间戳，判定时先淘汰窗口外过期记录，
 * 再看剩余容量。这是四种算法中<b>最精确</b>的一种——任意时刻回望一个
 * 完整窗口，通过数严格不超过上限，完全没有边界突发问题。
 *
 * <p>代价是内存 O(max)：每个许可一条记录。适合限额不大但要求严格精确的
 * 场景，例如每分钟 50 次的支付回调。
 */
public final class SlidingWindowLog implements RateLimiter {

    private final long windowNanos;
    private final int maxPerWindow;
    private final Clock clock;
    private final Deque<Long> log = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();

    SlidingWindowLog(long windowNanos, int maxPerWindow, Clock clock) {
        if (windowNanos <= 0) {
            throw new IllegalArgumentException("window must be > 0");
        }
        if (maxPerWindow <= 0) {
            throw new IllegalArgumentException("maxPerWindow must be > 0, got " + maxPerWindow);
        }
        this.windowNanos = windowNanos;
        this.maxPerWindow = maxPerWindow;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean tryAcquire(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits must be > 0, got " + permits);
        }
        lock.lock();
        try {
            long now = clock.nanoTime();
            long windowFloor = now - windowNanos;
            // 窗口是半开区间 (now - window, now]：恰好踩在下界的时间戳已过期
            while (!log.isEmpty() && log.peekFirst() <= windowFloor) {
                log.pollFirst();
            }
            if (log.size() + permits <= maxPerWindow) {
                for (int i = 0; i < permits; i++) {
                    log.addLast(now);
                }
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /** 当前窗口内的许可数（先淘汰过期项再统计，供监控/测试观察）。 */
    public int currentWindowCount() {
        lock.lock();
        try {
            long windowFloor = clock.nanoTime() - windowNanos;
            while (!log.isEmpty() && log.peekFirst() <= windowFloor) {
                log.pollFirst();
            }
            return log.size();
        } finally {
            lock.unlock();
        }
    }
}
