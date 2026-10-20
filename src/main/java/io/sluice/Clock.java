package io.sluice;

/**
 * 可注入的时间源。
 *
 * <p>限流的全部行为都建立在"现在几点"之上。把时间做成接口注入，
 * 单元测试就能精确推演毫秒级的窗口滚动与令牌补充，不必 Thread.sleep 猜运气。
 *
 * <p>生产环境用 {@link #system()}；测试环境传一个可拨动指针的假时钟。
 */
@FunctionalInterface
public interface Clock {

    /** 相对纳秒时间戳（与 {@link System#nanoTime()} 同语义，只保证单调、不保证绝对时刻）。 */
    long nanoTime();

    /** 系统时钟。 */
    static Clock system() {
        return System::nanoTime;
    }
}
