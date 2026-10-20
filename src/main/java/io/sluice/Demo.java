package io.sluice;

/**
 * 演示入口：对同一个「容量 5、每秒补 10」的令牌桶以约 12.5 QPS 连发 15 个请求，
 * 直观感受令牌桶"先放突发、后按配额"的节奏。
 *
 * <pre>
 * javac -d build $(find src/main/java -name '*.java')
 * java -cp build io.sluice.Demo
 * </pre>
 */
public final class Demo {

    private Demo() {
    }

    public static void main(String[] args) throws InterruptedException {
        RateLimiter limiter = Sluice.tokenBucket()
                .capacity(3)
                .refillPerSecond(10)
                .build();

        System.out.println("Sluice demo: token bucket (capacity=3, refill=10/s), 15 req @ ~25 QPS");
        System.out.println("-".repeat(72));

        int pass = 0;
        for (int i = 1; i <= 15; i++) {
            boolean ok = limiter.tryAcquire();
            if (ok) {
                pass++;
            }
            System.out.printf("req %02d -> %s  (available=%.2f)%n",
                    i, ok ? "PASS  " : "REJECT", ((TokenBucket) limiter).availableTokens());
            Thread.sleep(40);
        }

        System.out.println("-".repeat(72));
        System.out.printf("total: %d passed, %d rejected%n", pass, 15 - pass);
    }
}
