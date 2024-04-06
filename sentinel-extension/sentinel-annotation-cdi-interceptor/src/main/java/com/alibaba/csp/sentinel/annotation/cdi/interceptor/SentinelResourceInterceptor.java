package com.alibaba.csp.sentinel.annotation.cdi.interceptor;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;

import javax.annotation.Priority;
import javax.interceptor.AroundInvoke;
import javax.interceptor.Interceptor;
import javax.interceptor.InvocationContext;

/**
 * Sentinel资源拦截器
 *
 * @author sea
 * @since 1.8.0
 */
@Interceptor
@SentinelResourceBinding
@Priority(0)
public class SentinelResourceInterceptor extends AbstractSentinelInterceptorSupport {

    @AroundInvoke
    Object aroundInvoke(InvocationContext ctx) throws Throwable {
        SentinelResourceBinding annotation = ctx.getMethod().getAnnotation(SentinelResourceBinding.class);
        if (annotation == null) {
            throw new IllegalStateException("Wrong state for SentinelResource annotation");
        }

        String resourceName = getResourceName(annotation.value(), ctx.getMethod());
        EntryType entryType = annotation.entryType();
        int resourceType = annotation.resourceType();
        Entry entry = null;
        try {
            entry = SphU.entry(resourceName, resourceType, entryType, ctx.getParameters());
            Object result = ctx.proceed();
            return result;
        } catch (BlockException ex) {
            return handleBlockException(ctx, annotation, ex);
        } catch (Throwable ex) {
            Class<? extends Throwable>[] exceptionsToIgnore = annotation.exceptionsToIgnore();
            // The ignore list will be checked first.
            if (exceptionsToIgnore.length > 0 && exceptionBelongsTo(ex, exceptionsToIgnore)) {
                throw ex;
            }
            if (exceptionBelongsTo(ex, annotation.exceptionsToTrace())) {
                traceException(ex);
                return handleFallback(ctx, annotation, ex);
            }

            // No fallback function can handle the exception, so throw it out.
            throw ex;
        } finally {
            if (entry != null) {
                entry.exit(1, ctx.getParameters());
            }
        }
    }
}
