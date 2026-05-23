package com.orbix.engine.modules.iam.domain;

/**
 * The break-glass super administrator. Its username is a fixed constant (never
 * configurable) so the account is unambiguous and protectable across the
 * codebase. The account is created by the env-driven bootstrap as a
 * company-wide ADMIN with no default branch, and is shielded from destructive
 * edits — disable, profile/branch change, password reset, role grant/revoke —
 * so it can never be locked out of the system. See {@code RootAdminGuard}.
 */
public final class RootAdmin {

    /** Reserved username — must match the bootstrap and all guards. */
    public static final String USERNAME = "rootadmin";

    /** Fixed display name (not configurable). */
    public static final String DISPLAY_NAME = "Root Administrator";

    private RootAdmin() {}

    /** True if the given username is the reserved rootadmin (case-insensitive). */
    public static boolean is(String username) {
        return username != null && USERNAME.equalsIgnoreCase(username.trim());
    }
}
