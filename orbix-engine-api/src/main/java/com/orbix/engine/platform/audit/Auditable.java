package com.orbix.engine.platform.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose execution must produce an audit record.
 * {@link AuditAspect} inspects the method's arguments / return value to
 * record actor, action, entity, before/after state.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Auditable {
    String action();
    String entityType();
}
