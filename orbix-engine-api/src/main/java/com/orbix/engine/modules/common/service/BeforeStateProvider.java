package com.orbix.engine.modules.common.service;

/**
 * Optional marker interface for service implementations that want to supply a
 * before-state snapshot for UPDATE audit rows.
 *
 * <p>When {@link AuditAspect} detects that the join-point target implements
 * this interface and the action is {@code UPDATE}, it calls
 * {@link #captureBeforeState(Object)} with the first method argument (typically
 * a {@code uid} String) <em>before</em> the mutating method executes. The
 * returned JSON is stored as {@code before_json} in the audit row.
 *
 * <p>Implementations should look up the current entity state, serialise it to a
 * compact string (e.g. via the entity's {@code toString()} or the response DTO),
 * and return it. Return {@code null} if no snapshot is available (the aspect
 * treats null and a thrown exception identically — beforeJson stays null and the
 * audit write still succeeds).
 */
public interface BeforeStateProvider {

    /**
     * @param firstArg the first argument of the @Auditable UPDATE method,
     *                 usually the entity uid ({@code String}) or id ({@code Long}).
     * @return a serialised before-state string, or {@code null}.
     */
    String captureBeforeState(Object firstArg);
}
