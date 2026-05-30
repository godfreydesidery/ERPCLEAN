package com.orbix.engine.modules.iam.domain.dto;

/**
 * Minimal identity projection returned by the user-lookup endpoint.
 *
 * <p>This projection is intentionally narrow — id, uid, displayName,
 * username — so it can be exposed to any authenticated user without leaking
 * sensitive fields (email, phone, status, lock state, roles, last-login).
 * The lookup is read-only and scoped to the caller's company; it does not
 * confer any ability to modify the matched user or bypass the business-level
 * authorisation on any action the caller might trigger with the returned uid.
 *
 * <p>{@code id} serialises as a JSON string on the wire via the global
 * {@code IdLongAsStringSerializerModifier} — no per-field annotation needed.
 */
public record UserLookupDto(
    Long id,
    String uid,
    String displayName,
    String username
) {}
