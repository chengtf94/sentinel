package com.alibaba.csp.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.system.SystemRule;

/**
 * @author Eric Zhao
 * @since 1.7.0
 */
public interface SphResourceTypeSupport {

    /** 执行资源统计、Slot规则检查 */
    Entry entryWithType(String name, int resourceType, EntryType trafficType, int batchCount, Object[] args)
        throws BlockException;
    Entry entryWithType(String name, int resourceType, EntryType trafficType, int batchCount, boolean prioritized,
                        Object[] args) throws BlockException;

    /** 执行资源统计、Slot规则检查（异步） */
    AsyncEntry asyncEntryWithType(String name, int resourceType, EntryType trafficType, int batchCount,
                                  boolean prioritized,
                                  Object[] args) throws BlockException;
}
