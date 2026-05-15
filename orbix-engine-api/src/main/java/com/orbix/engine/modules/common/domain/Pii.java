package com.orbix.engine.modules.common.domain;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field as personally identifiable information. Such fields are
 * candidates for redaction from the audit log and from outbound domain
 * events.
 *
 * <p>Lives in the {@code domain} layer so entities may reference it. The
 * marker is in place now; the {@code AuditAspect} / {@code EventPublisher}
 * scrubbing that consumes it is a follow-up (it touches shared cross-cutting
 * infrastructure). Until then this annotation documents intent.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Pii {
}
