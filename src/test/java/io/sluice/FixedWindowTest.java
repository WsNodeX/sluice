package io.sluice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FixedWindowLimiter 固定窗口")
class FixedWindowTest {

    @Test
    @DisplayName("窗口内恰好放行 max 个，超出拒绝")
    void allowsExactlyMaxPerWindow() {
        FakeClock clock = new FakeClock();
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(3).clock(clock).build();

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire(), "窗口额度用尽应拒绝");
    }

    @Test
    @DisplayName("窗口翻转后计数清零")
    void resetsOnWindowRollover() {
        FakeClock clock = new FakeClock();
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(2).clock(clock).build();

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());

        clock.advanceMillis(1000);      // 恰好到达窗口边界
        assertTrue(limiter.tryAcquire(), "新窗口额度应重置");
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }

    @Test
    @DisplayName("长时间空闲后跳过多整窗，直接进入全新窗口")
    void skipsWholeWindowsAfterIdle() {
        FakeClock clock = new FakeClock();
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(2).clock(clock).build();

        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        clock.advanceSeconds(10);       // 跨了 10 个窗口
        assertTrue(limiter.tryAcquire(), "跨整窗后应全新计数");
    }

    @Test
    @DisplayName("批量获取受窗口额度约束")
    void multiPermitRespectsQuota() {
        FakeClock clock = new FakeClock();
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(5).clock(clock).build();

        assertTrue(limiter.tryAcquire(3));   // 剩 2
        assertFalse(limiter.tryAcquire(3));  // 3 > 2
        assertTrue(limiter.tryAcquire(2));   // 恰好用完
        assertFalse(limiter.tryAcquire());
    }

    @Test
    @DisplayName("单次请求超过窗口总额度：直接拒绝")
    void permitLargerThanQuotaIsAlwaysRejected() {
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(3).build();
        assertFalse(limiter.tryAcquire(4));
    }

    @Test
    @DisplayName("非法参数被拒绝")
    void rejectsInvalidArguments() {
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(2).build();
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class,
                () -> Sluice.fixedWindow().window(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                () -> Sluice.fixedWindow().maxPerWindow(0).build());
    }
}
