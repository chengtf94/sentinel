package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.List;
import java.util.concurrent.atomic.LongAdder;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.statistic.base.LeapArray;
import com.alibaba.csp.sentinel.slots.statistic.base.WindowWrap;
import com.alibaba.csp.sentinel.util.AssertUtil;

import static com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_EXCEPTION_COUNT;
import static com.alibaba.csp.sentinel.slots.block.RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO;

/**
 * 异常断路器：适用于熔断策略为异常比例或异常数
 *
 * @author Eric Zhao
 * @since 1.8.0
 */
public class ExceptionCircuitBreaker extends AbstractCircuitBreaker {

    /** 熔断策略、最低请求数、阈值、统计数据结构（基于LeapArray，包括错误数量、总数量） */
    private final int strategy;
    private final int minRequestAmount;
    private final double threshold;
    private final LeapArray<SimpleErrorCounter> stat;

    /** 构造方法 */
    public ExceptionCircuitBreaker(DegradeRule rule) {
        this(rule, new SimpleErrorCounterLeapArray(1, rule.getStatIntervalMs()));
    }
    ExceptionCircuitBreaker(DegradeRule rule, LeapArray<SimpleErrorCounter> stat) {
        super(rule);
        this.strategy = rule.getGrade();
        boolean modeOk = strategy == DEGRADE_GRADE_EXCEPTION_RATIO || strategy == DEGRADE_GRADE_EXCEPTION_COUNT;
        AssertUtil.isTrue(modeOk, "rule strategy should be error-ratio or error-count");
        AssertUtil.notNull(stat, "stat cannot be null");
        this.minRequestAmount = rule.getMinRequestAmount();
        this.threshold = rule.getCount();
        this.stat = stat;
    }

    @Override
    protected void resetStat() {
        // 重置当前窗口的统计数据
        stat.currentWindow().value().reset();
    }

    @Override
    public void onRequestComplete(Context context) {
        Entry entry = context.getCurEntry();
        if (entry == null) {
            return;
        }
        Throwable error = entry.getError();
        SimpleErrorCounter counter = stat.currentWindow().value();
        if (error != null) {
            // 错误数加1
            counter.getErrorCount().add(1);
        }
        // 总数量加1
        counter.getTotalCount().add(1);
        // 当到达阈值后处理状态转移
        handleStateChangeWhenThresholdExceeded(error);
    }

    /** 当到达阈值后处理状态转移 */
    private void handleStateChangeWhenThresholdExceeded(Throwable error) {
        if (currentState.get() == State.OPEN) {
            // #1 打开状态：不处理
        } else  if (currentState.get() == State.HALF_OPEN) {
            // #2 半打开状态：探测请求成功，则执行执行状态转移：HALF_OPEN -> CLOSE；否则执行状态转移：HALF_OPEN -> OPEN
            if (error == null) {
                fromHalfOpenToClose();
            } else {
                fromHalfOpenToOpen(1.0d);
            }
        } else {
            // #3 关闭状态：统计错误数量、总数量，若小于最低请求数则不处理，否则判断错误数或错误率是否超过阈值，超过则执行状态转移：CLOSED或HALF_OPEN -> OPEN
            List<SimpleErrorCounter> counters = stat.values();
            long errCount = 0;
            long totalCount = 0;
            for (SimpleErrorCounter counter : counters) {
                errCount += counter.errorCount.sum();
                totalCount += counter.totalCount.sum();
            }
            if (totalCount < minRequestAmount) {
                return;
            }
            double curCount = errCount;
            if (strategy == DEGRADE_GRADE_EXCEPTION_RATIO) {
                // Use errorRatio
                curCount = errCount * 1.0d / totalCount;
            }
            if (curCount > threshold) {
                transformToOpen(curCount);
            }
        }
    }

    /** 错误计数器 */
    static class SimpleErrorCounter {

        /** 错误数量、总数量 */
        private LongAdder errorCount;
        private LongAdder totalCount;

        public SimpleErrorCounter() {
            this.errorCount = new LongAdder();
            this.totalCount = new LongAdder();
        }

        public LongAdder getErrorCount() {
            return errorCount;
        }

        public LongAdder getTotalCount() {
            return totalCount;
        }

        /** 重置统计数据 */
        public SimpleErrorCounter reset() {
            errorCount.reset();
            totalCount.reset();
            return this;
        }

        @Override
        public String toString() {
            return "SimpleErrorCounter{" +
                    "errorCount=" + errorCount +
                    ", totalCount=" + totalCount +
                    '}';
        }
    }

    /** 错误计数器LeapArray */
    static class SimpleErrorCounterLeapArray extends LeapArray<SimpleErrorCounter> {

        public SimpleErrorCounterLeapArray(int sampleCount, int intervalInMs) {
            super(sampleCount, intervalInMs);
        }

        @Override
        public SimpleErrorCounter newEmptyBucket(long timeMillis) {
            return new SimpleErrorCounter();
        }

        @Override
        protected WindowWrap<SimpleErrorCounter> resetWindowTo(WindowWrap<SimpleErrorCounter> w, long startTime) {
            // Update the start time and reset value.
            w.resetTo(startTime);
            w.value().reset();
            return w;
        }
    }

}
