package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import java.util.concurrent.atomic.AtomicReference;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRuleManager;
import com.alibaba.csp.sentinel.util.AssertUtil;
import com.alibaba.csp.sentinel.util.TimeUtil;

/**
 * 断路器基类
 *
 * @author Eric Zhao
 * @since 1.8.0
 */
public abstract class AbstractCircuitBreaker implements CircuitBreaker {

    /** 断路器规则、恢复时间、事件观察者注册中心、当前状态、下一个尝试探针请求的时间 */
    protected final DegradeRule rule;
    protected final int recoveryTimeoutMs;
    private final EventObserverRegistry observerRegistry;
    protected final AtomicReference<State> currentState = new AtomicReference<>(State.CLOSED);
    protected volatile long nextRetryTimestamp;

    /** 构造方法 */
    public AbstractCircuitBreaker(DegradeRule rule) {
        this(rule, EventObserverRegistry.getInstance());
    }
    AbstractCircuitBreaker(DegradeRule rule, EventObserverRegistry observerRegistry) {
        AssertUtil.notNull(observerRegistry, "observerRegistry cannot be null");
        if (!DegradeRuleManager.isValidRule(rule)) {
            throw new IllegalArgumentException("Invalid DegradeRule: " + rule);
        }
        this.observerRegistry = observerRegistry;
        this.rule = rule;
        this.recoveryTimeoutMs = rule.getTimeWindow() * 1000;
    }

    @Override
    public boolean tryPass(Context context) {
        if (currentState.get() == State.CLOSED) {
            // 关闭状态
            return true;
        } else if (currentState.get() == State.OPEN) {
            // 打开状态：若到达尝试探针请求的时间，则允许放行该个请求作为探针
            return retryTimeoutArrived() && fromOpenToHalfOpen(context);
        } else {
            // 半打开状态
            return false;
        }
    }
    protected boolean retryTimeoutArrived() {
        return TimeUtil.currentTimeMillis() >= nextRetryTimestamp;
    }
    protected boolean fromOpenToHalfOpen(Context context) {
        // #1 更新状态OPEN -> HALF_OPEN，通知事件监听者状态变更
        if (currentState.compareAndSet(State.OPEN, State.HALF_OPEN)) {
            notifyObservers(State.OPEN, State.HALF_OPEN, null);
            Entry entry = context.getCurEntry();
            entry.whenTerminate((context1, entry1) -> {
                // #2 执行该请求，若该请求最终执行失败，则回退为OPEN状态
                if (entry1.getBlockError() != null) {
                    // Fallback to OPEN due to detecting request is blocked
                    currentState.compareAndSet(State.HALF_OPEN, State.OPEN);
                    notifyObservers(State.HALF_OPEN, State.OPEN, 1.0d);
                }
            });
            return true;
        }
        return false;
    }

    /** 通知事件监听者：状态OPEN -> HALF_OPEN */
    private void notifyObservers(CircuitBreaker.State prevState, CircuitBreaker.State newState, Double snapshotValue) {
        for (CircuitBreakerStateChangeObserver observer : observerRegistry.getStateChangeObservers()) {
            observer.onStateChange(prevState, newState, rule, snapshotValue);
        }
    }

    /** 执行状态转移：CLOSED或HALF_OPEN -> OPEN */
    protected void transformToOpen(double triggerValue) {
        State cs = currentState.get();
        switch (cs) {
            case CLOSED:
                fromCloseToOpen(triggerValue);
                break;
            case HALF_OPEN:
                fromHalfOpenToOpen(triggerValue);
                break;
            default:
                break;
        }
    }
    /** 执行状态转移：CLOSED -> OPEN */
    protected boolean fromCloseToOpen(double snapshotValue) {
        // #1 更新状态CLOSED -> OPEN，更新下一个尝试探针请求的时间，并通知事件监听者状态变更
        State prev = State.CLOSED;
        if (currentState.compareAndSet(prev, State.OPEN)) {
            updateNextRetryTimestamp();
            notifyObservers(prev, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }
    /** 执行状态转移：HALF_OPEN -> OPEN */
    protected boolean fromHalfOpenToOpen(double snapshotValue) {
        // #1 更新状态HALF_OPEN -> OPEN，更新下一个尝试探针请求的时间，并通知事件监听者状态变更
        if (currentState.compareAndSet(State.HALF_OPEN, State.OPEN)) {
            updateNextRetryTimestamp();
            notifyObservers(State.HALF_OPEN, State.OPEN, snapshotValue);
            return true;
        }
        return false;
    }
    /** 执行状态转移：HALF_OPEN -> CLOSE */
    protected boolean fromHalfOpenToClose() {
        // #1 更新状态HALF_OPEN -> OPEN，重置统计数据，并通知事件监听者状态变更
        if (currentState.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
            resetStat();
            notifyObservers(State.HALF_OPEN, State.CLOSED, null);
            return true;
        }
        return false;
    }
    /** 更新下一个尝试探针请求的时间 */
    protected void updateNextRetryTimestamp() {
        this.nextRetryTimestamp = TimeUtil.currentTimeMillis() + recoveryTimeoutMs;
    }
    /** 重置统计数据 */
    abstract void resetStat();

    @Override
    public DegradeRule getRule() {
        return rule;
    }

    @Override
    public State currentState() {
        return currentState.get();
    }

}
