package io.sluice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Sluice 门面与默认值")
class SluiceFacadeTest {

    @Test
    @DisplayName("默认配置开箱即用：固定窗口 100/s")
    void fixedWindowDefaultsWork() {
        RateLimiter limiter = Sluice.fixedWindow().build();
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryAcquire(), "默认 maxPerWindow=100，前 100 个应通过");
        }
        assertFalse(limiter.tryAcquire());
    }

    @Test
    @DisplayName("四种算法都能从门面构建")
    void buildsAllImplementations() {
        assertTrue(Sluice.tokenBucket().build() instanceof TokenBucket);
        assertTrue(Sluice.fixedWindow().build() instanceof FixedWindowLimiter);
        assertTrue(Sluice.slidingWindowLog().build() instanceof SlidingWindowLog);
        assertTrue(Sluice.slidingWindowCounter().build() instanceof SlidingWindowCounter);
    }

    @Test
    @DisplayName("注入的时钟被原样传递并驱动限流行为")
    void injectedClockIsPropagated() {
        FakeClock fake = new FakeClock();
        FixedWindowLimiter limiter = Sluice.fixedWindow()
                .window(Duration.ofSeconds(1)).maxPerWindow(1).clock(fake).build();
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        fake.advanceSeconds(1);
        assertTrue(limiter.tryAcquire(), "假时钟推进后应翻窗");
    }

    @Test
    @DisplayName("Duration 为空或非正时构建失败")
    void rejectsBadDuration() {
        assertThrows(NullPointerException.class, () -> Sluice.fixedWindow().window(null).build());
        assertThrows(IllegalArgumentException.class,
                () -> Sluice.slidingWindowLog().window(Duration.ZERO).build());
        assertThrows(IllegalArgumentException.class,
                () -> Sluice.slidingWindowCounter().window(Duration.ofMillis(-1)).build());
    }

    @Test
    @DisplayName("Duration 在构建器上即时校验")
    void durationValidatedEagerly() {
        assertThrows(IllegalArgumentException.class, () -> Sluice.fixedWindow().window(Duration.ZERO));
    }
}
