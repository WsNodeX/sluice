package io.sluice;

/**
 * 限流器的统一抽象。
 *
 * <p>所有实现必须满足：
 * <ul>
 *   <li><b>线程安全</b>——多线程并发调用不超卖许可；</li>
 *   <li><b>非阻塞</b>——{@code tryAcquire} 立即返回，不等待；</li>
 *   <li><b>确定性可测</b>——时间由 {@link Clock} 注入，测试可精确复现任意时刻。</li>
 * </ul>
 */
public interface RateLimiter {

    /**
     * 尝试获取 1 个许可。
     *
     * @return 允许通过返回 {@code true}；超出限额返回 {@code false}（不消耗任何许可）
     */
    default boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试一次性获取 {@code permits} 个许可。要么全部成功，要么全部失败——
     * 不存在"拿走一半"的中间态。
     *
     * @param permits 需要的许可数，必须 &gt; 0
     * @return 允许通过返回 {@code true}
     * @throws IllegalArgumentException permits &le; 0
     */
    boolean tryAcquire(int permits);
}
