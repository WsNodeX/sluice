package io.sluice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SlidingWindowLog 滑动日志")
class SlidingWindowLogTest {

    @Test
    @DisplayName("窗口内精确限额：任意时刻回望一窗，通过数不超过上限")
    void enforcesExactLimitInsideWindow() {
        FakeClock clock = new FakeClock();
        SlidingWindowLog limiter = Sluice.slidingWindowLog()
                .window(Duration.ofSeconds(1)).maxPerWindow(3).clock(clock).build();

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "窗口内第 4 个应拒绝");
    }

    @Test
    @DisplayName("许可随时间滑动过期：过期一个，立即腾出一个名额")
    void permitsExpireSlidingly() {
        FakeClock clock = new FakeClock();
        SlidingWindowLog limiter = Sluice.slidingWindowLog()
                .window(Duration.ofSeconds(1)).maxPerWindow(3).clock(clock).build();

        // t=0ms：放 3 个
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertEquals(3, limiter.currentWindowCount());

        // t=400ms：仍占满 3 个
        clock.advanceMillis(400);
        assertFalse(limiter.tryAcquire());

        // t=1100ms：t=0 的 3 条全部过期（窗口半开区间下界 100ms），又腾出 3 个
        clock.advanceMillis(700);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    @DisplayName("部分过期：只腾出过期数量的名额")
    void partiallyEvicts() {
        FakeClock clock = new FakeClock();
        SlidingWindowLog limiter = Sluice.slidingWindowLog()
                .window(Duration.ofSeconds(1)).maxPerWindow(3).clock(clock).build();

        assertTrue(limiter.tryAcquire());                    // t=0
        clock.advanceMillis(500);
        assertTrue(limiter.tryAcquire());                    // t=500
        assertTrue(limiter.tryAcquire());                    // t=500

        clock.advanceMillis(700);                            // t=1200，窗口下界 200ms
        // t=0 的记录已过期，t=500 的两条仍在 → 只剩 1 个名额
        assertTrue(limiter.tryAcquire(), "过期 1 条应腾出 1 个名额");
        assertFalse(limiter.tryAcquire());
        assertEquals(3, limiter.currentWindowCount());
    }

    @Test
    @DisplayName("批量获取的原子性：剩余名额不足时整批拒绝")
    void multiPermitIsAtomic() {
        FakeClock clock = new FakeClock();
        SlidingWindowLog limiter = Sluice.slidingWindowLog()
                .window(Duration.ofSeconds(1)).maxPerWindow(3).clock(clock).build();

        assertTrue(limiter.tryAcquire(2));                   // 占 2，剩 1
        assertFalse(limiter.tryAcquire(2), "差 1 个名额，2 个应整批拒绝");
        assertTrue(limiter.tryAcquire());                    // 用掉最后 1 个
        assertEquals(3, limiter.currentWindowCount());
    }
}
