package io.sluice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("TokenBucket 令牌桶")
class TokenBucketTest {

    @Test
    @DisplayName("满桶起步：容量内可连续放行，超出即拒绝")
    void startsFullAndAllowsBurstUpToCapacity() {
        TokenBucket bucket = Sluice.tokenBucket().capacity(5).refillPerSecond(1).build();
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryAcquire(), "第 " + (i + 1) + " 个请求应通过");
        }
        assertFalse(bucket.tryAcquire(), "超出容量应拒绝");
    }

    @Test
    @DisplayName("拒绝时不消耗任何令牌")
    void rejectedRequestConsumesNothing() {
        FakeClock clock = new FakeClock();
        TokenBucket bucket = Sluice.tokenBucket().capacity(3).refillPerSecond(1).clock(clock).build();
        assertTrue(bucket.tryAcquire(3));
        assertFalse(bucket.tryAcquire());
        assertEquals(0.0, bucket.availableTokens(), 0.0, "拒绝的请求不应消耗令牌");
    }

    @Test
    @DisplayName("按流逝时间匀速补充令牌")
    void refillsProportionallyToElapsedTime() {
        FakeClock clock = new FakeClock();
        TokenBucket bucket = Sluice.tokenBucket().capacity(5).refillPerSecond(10).clock(clock).build();

        // 耗尽
        for (int i = 0; i < 5; i++) {
            assertTrue(bucket.tryAcquire());
        }
        assertFalse(bucket.tryAcquire());

        // 200ms 只补 2 个（10/s × 0.2s）
        clock.advanceMillis(200);
        assertTrue(bucket.tryAcquire());
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire(), "只补了 2 个，第 3 个应拒绝");
    }

    @Test
    @DisplayName("补充量封顶于桶容量，不会溢出")
    void refillNeverExceedsCapacity() {
        FakeClock clock = new FakeClock();
        TokenBucket bucket = Sluice.tokenBucket().capacity(4).refillPerSecond(10).clock(clock).build();

        assertTrue(bucket.tryAcquire());                     // 剩 3
        clock.advanceSeconds(100);                            // 足够补满 N 桶
        assertEquals(4.0, bucket.availableTokens(), 1e-9, "令牌数必须封顶在容量");
    }

    @Test
    @DisplayName("批量获取：要么全部成功要么全部失败")
    void permitsAreAllOrNothing() {
        TokenBucket bucket = Sluice.tokenBucket().capacity(5).refillPerSecond(1).build();
        assertTrue(bucket.tryAcquire(3));                    // 剩 2
        assertFalse(bucket.tryAcquire(3), "3 > 剩余 2，应整体失败");
        assertTrue(bucket.tryAcquire(2), "2 <= 剩余 2，应成功");
    }

    @Test
    @DisplayName("单次请求超过整个容量：永远拒绝")
    void permitLargerThanCapacityIsAlwaysRejected() {
        TokenBucket bucket = Sluice.tokenBucket().capacity(3).refillPerSecond(100).build();
        assertFalse(bucket.tryAcquire(4));
        assertTrue(bucket.tryAcquire(3));
    }

    @Test
    @DisplayName("非法参数被拒绝")
    void rejectsInvalidArguments() {
        TokenBucket bucket = Sluice.tokenBucket().capacity(2).refillPerSecond(1).build();
        assertThrows(IllegalArgumentException.class, () -> bucket.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> bucket.tryAcquire(-1));
        assertThrows(IllegalArgumentException.class, () -> Sluice.tokenBucket().capacity(0).build());
        assertThrows(IllegalArgumentException.class, () -> Sluice.tokenBucket().capacity(1).refillPerSecond(0).build());
    }
}
