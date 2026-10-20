package io.sluice;

/** 可拨动的假时钟：测试里手动推进时间，行为完全确定。 */
final class FakeClock implements Clock {

    private long now;

    @Override
    public long nanoTime() {
        return now;
    }

    void advanceNanos(long nanos) {
        now += nanos;
    }

    void advanceMillis(long millis) {
        now += millis * 1_000_000L;
    }

    void advanceSeconds(long seconds) {
        advanceMillis(seconds * 1000L);
    }
}
