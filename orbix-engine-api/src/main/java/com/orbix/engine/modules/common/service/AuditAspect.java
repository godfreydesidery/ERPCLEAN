package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.service.RequestContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row for every invocation of an @Auditable method.
 * The aspect is the single point where audit happens — services must not
 * call auditing directly. See ARCHITECTURE.md §2.7.
 */
@Aspect
@Component
public class AuditAspect {

    private final AuditLogWriter writer;
    private final RequestContext context;

    public AuditAspect(AuditLogWriter writer, RequestContext context) {
        this.writer = writer;
        this.context = context;
    }

    @Around("@annotation(com.orbix.engine.modules.common.service.Auditable)")
    public Object record(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Auditable ann = sig.getMethod().getAnnotation(Auditable.class);

        Object result = pjp.proceed();
        try {
            writer.write(new AuditLogWriter.Record(
                context.userId(),
                context.companyId(),
                context.branchId(),
                ann.action(),
                ann.entityType(),
                result == null ? null : result.toString()
            ));
        } catch (Exception ex) {
            // Audit failure must not silently swallow business errors,
            // but must also not roll back a successful business write.
            // The writer logs and queues for retry.
        }
        return result;
    }
}
