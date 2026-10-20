package io.sluice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("并发安全")
class ConcurrencyTest {

    @Test
    @DisplayName("固定窗口：400 次并发抢购恰好放行 100 个，不超卖")
    void fixedWindowNeverOversells() throws Exception {
        int threads = 8;
        int attemptsPerThread = 50;
        int maxPerWindow = 100;

        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(10)).maxPerWindow(maxPerWindow).build();

        int allowed = hammer(limiter, threads, attemptsPerThread);
        assertEquals(maxPerWindow, allowed, "放行数必须精确等于窗口额度");
    }

    @Test
    @DisplayName("令牌桶：容量 200 无足够补充时，并发放行不超过 200")
    void tokenBucketNeverOversells() throws Exception {
        int threads = 8;
        int attemptsPerThread = 50;
        int capacity = 200;

        TokenBucket bucket = Sluice.tokenBucket()
                .capacity(capacity).refillPerSecond(0.001).build();

        int allowed = hammer(bucket, threads, attemptsPerThread);
        assertEquals(capacity, allowed, "放行数必须精确等于初始令牌数");
    }

    @Test
    @DisplayName("滑动日志：并发下不超卖窗口额度")
    void slidingLogNeverOversells() throws Exception {
        int maxPerWindow = 120;
        SlidingWindowLog limiter = Sluice.slidingWindowLog()
                .window(Duration.ofSeconds(10)).maxPerWindow(maxPerWindow).build();

        int allowed = hammer(limiter, 8, 40);
        assertEquals(maxPerWindow, allowed);
    }

    /** threads × attemptsPerThread 次并发 tryAcquire，返回实际放行总数。 */
    private int hammer(RateLimiter limiter, int threads, int attemptsPerThread) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger allowed = new AtomicInteger();

            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    for (int i = 0; i < attemptsPerThread; i++) {
                        if (limiter.tryAcquire()) {
                            allowed.incrementAndGet();
                        }
                    }
                }));
            }
            ready.await();
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
            return allowed.get();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError("并发测试中出现异常", e.getCause());
        } finally {
            pool.shutdownNow();
        }
    }
}
