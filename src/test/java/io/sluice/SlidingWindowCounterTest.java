package io.sluice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("SlidingWindowCounter 滑动计数")
class SlidingWindowCounterTest {

    @Test
    @DisplayName("当前窗口内按全额计数")
    void countsFullyInsideCurrentWindow() {
        FakeClock clock = new FakeClock();
        SlidingWindowCounter limiter = Sluice.slidingWindowCounter()
                .window(Duration.ofSeconds(1)).maxPerWindow(5).clock(clock).build();

        for (int i = 0; i < 5; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire(), "当前窗口内 5 个用尽应拒绝");
    }

    @Test
    @DisplayName("跨窗后按时间比例折减上一窗口的用量")
    void weightsPreviousWindowByElapsedRatio() {
        FakeClock clock = new FakeClock();
        SlidingWindowCounter limiter = Sluice.slidingWindowCounter()
                .window(Duration.ofSeconds(1)).maxPerWindow(10).clock(clock).build();

        // t=0：上一窗口打满 10
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryAcquire());
        }
        assertFalse(limiter.tryAcquire());

        // 翻到新窗口（t=1s），再走到 t=1.5s：上一窗口折算 10 × 0.5 = 5
        clock.advanceMillis(1500);
        assertTrue(limiter.tryAcquire(), "折算后应还有约 5 个余量");
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "5 + 5 已达上限，第 6 个应拒绝");
    }

    @Test
    @DisplayName("隔了完整窗口后计数完全清零")
    void fullyResetsAfterWholeIdleWindows() {
        FakeClock clock = new FakeClock();
        SlidingWindowCounter limiter = Sluice.slidingWindowCounter()
                .window(Duration.ofSeconds(1)).maxPerWindow(4).clock(clock).build();

        for (int i = 0; i < 4; i++) {
            assertTrue(limiter.tryAcquire());
        }
        clock.advanceMillis(2500);      // 跨过 2 个完整窗口
        assertTrue(limiter.tryAcquire(), "历史计数应完全过期");
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
    }

    @Test
    @DisplayName("非法参数被拒绝")
    void rejectsInvalidArguments() {
        SlidingWindowCounter limiter = Sluice.slidingWindowCounter()
                .window(Duration.ofSeconds(1)).maxPerWindow(2).build();
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class,
                () -> Sluice.slidingWindowCounter().window(Duration.ofMillis(-5)).build());
    }
}
