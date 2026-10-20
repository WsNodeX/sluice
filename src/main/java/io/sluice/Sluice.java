package io.sluice;

import java.time.Duration;
import java.util.Objects;

/**
 * 门面与构建器：所有算法从这里造，配置项在编译期即可校验。
 *
 * <pre>{@code
 * RateLimiter limiter = Sluice.tokenBucket()
 *         .capacity(100)
 *         .refillPerSecond(10)
 *         .build();
 *
 * if (limiter.tryAcquire()) {
 *     // 放行
 * }
 * }</pre>
 *
 * <p>每个 builder 默认给出开箱即用的合理值；测试时通过 {@code .clock(...)}
 * 注入假时钟，即可精确推演毫秒级行为。
 */
public final class Sluice {

    private Sluice() {
    }

    /** 令牌桶构建器。 */
    public static TokenBucketBuilder tokenBucket() {
        return new TokenBucketBuilder();
    }

    /** 固定窗口构建器。 */
    public static FixedWindowBuilder fixedWindow() {
        return new FixedWindowBuilder();
    }

    /** 滑动日志构建器。 */
    public static SlidingWindowLogBuilder slidingWindowLog() {
        return new SlidingWindowLogBuilder();
    }

    /** 滑动计数构建器。 */
    public static SlidingWindowCounterBuilder slidingWindowCounter() {
        return new SlidingWindowCounterBuilder();
    }

    // ------------------------------------------------------------------
    // Token Bucket
    // ------------------------------------------------------------------

    public static final class TokenBucketBuilder {
        private double capacity = 64;
        private double refillPerSecond = 8;
        private Clock clock = Clock.system();

        public TokenBucketBuilder capacity(double capacity) {
            this.capacity = requirePositive(capacity, "capacity");
            return this;
        }

        public TokenBucketBuilder refillPerSecond(double refillPerSecond) {
            this.refillPerSecond = requirePositive(refillPerSecond, "refillPerSecond");
            return this;
        }

        public TokenBucketBuilder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public TokenBucket build() {
            return new TokenBucket(capacity, refillPerSecond, clock);
        }
    }

    // ------------------------------------------------------------------
    // Fixed Window
    // ------------------------------------------------------------------

    public static final class FixedWindowBuilder {
        private Duration window = Duration.ofSeconds(1);
        private int maxPerWindow = 100;
        private Clock clock = Clock.system();

        public FixedWindowBuilder window(Duration window) {
            this.window = requirePositiveDuration(window);
            return this;
        }

        public FixedWindowBuilder maxPerWindow(int maxPerWindow) {
            this.maxPerWindow = requirePositiveInt(maxPerWindow, "maxPerWindow");
            return this;
        }

        public FixedWindowBuilder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public FixedWindowLimiter build() {
            return new FixedWindowLimiter(window.toNanos(), maxPerWindow, clock);
        }
    }

    // ------------------------------------------------------------------
    // Sliding Window Log
    // ------------------------------------------------------------------

    public static final class SlidingWindowLogBuilder {
        private Duration window = Duration.ofSeconds(1);
        private int maxPerWindow = 100;
        private Clock clock = Clock.system();

        public SlidingWindowLogBuilder window(Duration window) {
            this.window = requirePositiveDuration(window);
            return this;
        }

        public SlidingWindowLogBuilder maxPerWindow(int maxPerWindow) {
            this.maxPerWindow = requirePositiveInt(maxPerWindow, "maxPerWindow");
            return this;
        }

        public SlidingWindowLogBuilder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public SlidingWindowLog build() {
            return new SlidingWindowLog(window.toNanos(), maxPerWindow, clock);
        }
    }

    // ------------------------------------------------------------------
    // Sliding Window Counter
    // ------------------------------------------------------------------

    public static final class SlidingWindowCounterBuilder {
        private Duration window = Duration.ofSeconds(1);
        private int maxPerWindow = 100;
        private Clock clock = Clock.system();

        public SlidingWindowCounterBuilder window(Duration window) {
            this.window = requirePositiveDuration(window);
            return this;
        }

        public SlidingWindowCounterBuilder maxPerWindow(int maxPerWindow) {
            this.maxPerWindow = requirePositiveInt(maxPerWindow, "maxPerWindow");
            return this;
        }

        public SlidingWindowCounterBuilder clock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        public SlidingWindowCounter build() {
            return new SlidingWindowCounter(window.toNanos(), maxPerWindow, clock);
        }
    }

    // ------------------------------------------------------------------
    // 校验工具
    // ------------------------------------------------------------------

    private static double requirePositive(double v, String name) {
        if (v <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + v);
        }
        return v;
    }

    private static int requirePositiveInt(int v, String name) {
        if (v <= 0) {
            throw new IllegalArgumentException(name + " must be > 0, got " + v);
        }
        return v;
    }

    private static Duration requirePositiveDuration(Duration d) {
        Objects.requireNonNull(d, "window");
        if (d.isZero() || d.isNegative()) {
            throw new IllegalArgumentException("window must be positive, got " + d);
        }
        return d;
    }
}
