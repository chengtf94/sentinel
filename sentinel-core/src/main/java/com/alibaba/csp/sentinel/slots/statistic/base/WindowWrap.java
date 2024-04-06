package com.alibaba.csp.sentinel.slots.statistic.base;

/**
 * 时间窗口包装类
 *
 * @param <T> data type
 * @author jialiang.linjl
 * @author Eric Zhao
 */
public class WindowWrap<T> {

    /** 窗口大小、窗口起始时间、统计数据 */
    private final long windowLengthInMs;
    private long windowStart;
    private T value;

    /** 构造方法 */
    public WindowWrap(long windowLengthInMs, long windowStart, T value) {
        this.windowLengthInMs = windowLengthInMs;
        this.windowStart = windowStart;
        this.value = value;
    }

    /** 重置窗口起始时间 */
    public WindowWrap<T> resetTo(long startTime) {
        this.windowStart = startTime;
        return this;
    }

    /** 检查某个时间是否在该窗口内 */
    public boolean isTimeInWindow(long timeMillis) {
        return windowStart <= timeMillis && timeMillis < windowStart + windowLengthInMs;
    }

    public long windowLength() {
        return windowLengthInMs;
    }

    public long windowStart() {
        return windowStart;
    }

    public T value() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "WindowWrap{" +
            "windowLengthInMs=" + windowLengthInMs +
            ", windowStart=" + windowStart +
            ", value=" + value +
            '}';
    }

}
