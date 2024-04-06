package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * 响应时间断路器
 *
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ResponseTimeCircuitBreaker extends AbstractCircuitBreaker {
    private static final double SLOW_REQUEST_RATIO_MAX_VALUE = 1.0d;

    /** 最大RT、最大慢请求比例、最低请求数、统计数据结构（基于LeapArray，包括错误数量、总数量） */
    private final long maxAllowedRt;
    private final double maxSlowRequestRatio;
    private final int minRequestAmount;
    private final LeapArray<SlowRequestCounter> slidingCounter;

    /** 构造方法 */
    public ResponseTimeCircuitBreaker(DegradeRule rule) {
        this(rule, new SlowRequestLeapArray(1, rule.getStatIntervalMs()));
    }
    ResponseTimeCircuitBreaker(DegradeRule rule, LeapArray<SlowRequestCounter> stat) {
        super(rule);
        AssertUtil.isTrue(rule.getGrade() == RuleConstant.DEGRADE_GRADE_RT, "rule metric type should be RT");
        AssertUtil.notNull(stat, "stat cannot be null");
        this.maxAllowedRt = Math.round(rule.getCount());
        this.maxSlowRequestRatio = rule.getSlowRatioThreshold();
        this.minRequestAmount = rule.getMinRequestAmount();
        this.slidingCounter = stat;
    }

    @Override
    public void resetStat() {
        // 重置当前窗口的统计数据
        slidingCounter.currentWindow().value().reset();
    }

    @Override
    public void onRequestComplete(Context context) {
        SlowRequestCounter counter = slidingCounter.currentWindow().value();
        Entry entry = context.getCurEntry();
        if (entry == null) {
            return;
        }
        long completeTime = entry.getCompleteTimestamp();
        if (completeTime <= 0) {
            completeTime = TimeUtil.currentTimeMillis();
        }
        long rt = completeTime - entry.getCreateTimestamp();
        if (rt > maxAllowedRt) {
            // 慢请求加1
            counter.slowCount.add(1);
        }
        // 总数量加1
        counter.totalCount.add(1);
        // 当到达阈值后处理状态转移
        handleStateChangeWhenThresholdExceeded(rt);
    }

    /** 当到达阈值后处理状态转移 */
    private void handleStateChangeWhenThresholdExceeded(long rt) {
        if (currentState.get() == State.OPEN) {
            // #1 打开状态：不处理
        } else if (currentState.get() == State.HALF_OPEN) {
            // #2 半打开状态：探测请求RT满足要求，则执行执行状态转移：HALF_OPEN -> CLOSE；否则执行状态转移：HALF_OPEN -> OPEN
            if (rt <= maxAllowedRt) {
                fromHalfOpenToClose();
            } else {
                fromHalfOpenToOpen(1.0d);
            }
        } else {
            // #3 关闭状态：统计慢请求数、总数量，若小于最低请求数则不处理，否则判断慢请求比例是否超过阈值，超过则执行状态转移：CLOSED或HALF_OPEN -> OPEN
            List<SlowRequestCounter> counters = slidingCounter.values();
            long slowCount = 0;
            long totalCount = 0;
            for (SlowRequestCounter counter : counters) {
                slowCount += counter.slowCount.sum();
                totalCount += counter.totalCount.sum();
            }
            if (totalCount < minRequestAmount) {
                return;
            }
            double currentRatio = slowCount * 1.0d / totalCount;
            if (currentRatio > maxSlowRequestRatio) {
                transformToOpen(currentRatio);
            }
            if (Double.compare(currentRatio, maxSlowRequestRatio) == 0 &&
                    Double.compare(maxSlowRequestRatio, SLOW_REQUEST_RATIO_MAX_VALUE) == 0) {
                transformToOpen(currentRatio);
            }
        }
    }

    /** 慢请求计数器 */
    static class SlowRequestCounter {
        private LongAdder slowCount;
        private LongAdder totalCount;

        public SlowRequestCounter() {
            this.slowCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getSlowCount() {
            return slowCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        public SlowRequestCounter reset() {
            slowCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SlowRequestCounter{" +
                "slowCount=" + slowCount +
                ", totalCount=" + totalCount +
                '}';
        }
    }

    /** 慢请求计数器LeapArray */
    static class SlowRequestLeapArray extends LeapArray<SlowRequestCounter> {

        public SlowRequestLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SlowRequestCounter newEmptyBucket(long timeMillis) {
            return new SlowRequestCounter();
        }

        @Override
        protected WindowWrap<SlowRequestCounter> resetWindowTo(WindowWrap<SlowRequestCounter> w, long startTime) {
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }

    }
}
