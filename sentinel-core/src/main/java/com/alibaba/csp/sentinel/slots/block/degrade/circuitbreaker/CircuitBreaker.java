package com.alibaba.csp.sentinel.slots.block.degrade.circuitbreaker;

import com.alibaba.csp.sentinel.context.Context;
import com.alibaba.csp.sentinel.slotchain.ResourceWrapper;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeRule;

/**
 * 断路器接口
 *
 * @author Eric Zhao
 */
public interface CircuitBreaker {

    /** 获取关联的断路器规则 */
    DegradeRule getRule();

    /** 检查是否允许通过 */
    boolean tryPass(Context context);

    /** 获取当前的断路器状态 */
    State currentState();

    /** 将完成后的请求记录到统计上下文总，并尝试执行断路器的状态转移 */
    void onRequestComplete(Context context);

    /** 断路器状态机 */
    enum State {
        /** 打开状态：所有请求在下一个恢复时间点之前都被拒绝 */
        OPEN,
        /** 半打开状态：允许放行业务请求作为探针请求调用，若探针请求成功，则执行恢复策略，恢复至100%后进入关闭状态，否则仍然进入打开状态 */
        HALF_OPEN,
        /** 关闭状态：所有请求均被允许，若当前的异常指标超过配置的阈值，则进入打开状态 */
        CLOSED
    }

}
