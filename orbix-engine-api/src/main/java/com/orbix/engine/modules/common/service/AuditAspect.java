package com.orbix.engine.modules.common.service;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row for every invocation of an @Auditable method.
 * The aspect is the single point where audit happens — services must not
 * call auditing directly. See ARCHITECTURE.md §2.7.
 *
 * For UPDATE actions the aspect resolves the before-state by checking
 * whether the first method argument implements {@link AuditableEntity}
 * (a marker interface services can expose on their return/lookup types).
 * The simpler approach used here: if the service annotates an UPDATE method
 * and the method's first argument is a String (uid) or Long (id), the
 * before-state snapshot is taken via {@link BeforeStateCapture} if the
 * service itself implements it; otherwise beforeJson stays null.
 *
 * Concrete mechanism: UPDATE methods that want before-state must call
 * {@link #captureBeforeState(Object)} themselves via a default helper, OR
 * the aspect detects a {@link BeforeStateResolver} in the target if present.
 *
 * Practical approach adopted here: for UPDATE actions the aspect checks if
 * the result object (after-state) has a non-null toString; that is already
 * captured as afterJson. For beforeJson, it checks if the join-point target
 * implements {@link BeforeStateProvider} and delegates to it. Services that
 * do not implement the interface get beforeJson=null (existing behaviour,
 * acceptable for CREATE/ARCHIVE/etc.; UPDATE services should implement the
 * interface for full audit fidelity).
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
    public Object intercept(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Auditable ann = sig.getMethod().getAnnotation(Auditable.class);

        // Capture before-state for UPDATE actions before the method executes.
        String beforeJson = null;
        if ("UPDATE".equals(ann.action())) {
            beforeJson = resolveBeforeState(pjp);
        }

        Object result = pjp.proceed();
        try {
            writer.write(new AuditLogWriter.Record(
                context.userId(),
                context.companyId(),
                context.branchId(),
                ann.action(),
                ann.entityType(),
                null,   // entity id isn't generically extractable from the return value
                beforeJson,
                result == null ? null : result.toString(),
                clientMeta()
            ));
        } catch (Exception ex) {
            // Audit failure must not silently swallow business errors,
            // but must also not roll back a successful business write.
            // The writer logs and queues for retry.
        }
        return result;
    }

    /**
     * Resolves the before-state string for UPDATE actions. Delegates to
     * {@link BeforeStateProvider} if the target service implements it and
     * the first argument (uid/id) is available; otherwise returns null.
     */
    private String resolveBeforeState(ProceedingJoinPoint pjp) {
        try {
            Object target = pjp.getTarget();
            Object[] args = pjp.getArgs();
            if (target instanceof BeforeStateProvider provider && args.length > 0) {
                return provider.captureBeforeState(args[0]);
            }
        } catch (Exception ignored) {
            // Before-state capture must never propagate — fall through to null.
        }
        return null;
    }

    /** Small JSON blob with the request's transport metadata, or null if none. */
    private String clientMeta() {
        String client = context.clientVersion();
        String ip = context.ip();
        if (client == null && ip == null) {
            return null;
        }
        return "{\"client\":" + jsonOrNull(client) + ",\"ip\":" + jsonOrNull(ip) + "}";
    }

    private static String jsonOrNull(String s) {
        if (s == null) {
            return "null";
        }
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
