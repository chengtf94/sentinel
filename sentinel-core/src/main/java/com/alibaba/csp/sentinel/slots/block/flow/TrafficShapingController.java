package com.alibaba.csp.sentinel.slots.block.flow;

import com.alibaba.csp.sentinel.node.Node;

/**
 * 流量控制器接口
 *
 * @author jialiang.linjl
 */
public interface TrafficShapingController {

    /** 检查是否允许通过 */
    boolean canPass(Node node, int acquireCount, boolean prioritized);
    boolean canPass(Node node, int acquireCount);

}
