package com.orbix.engine.modules.common.service;

/**
 * Immediate access-token revocation (US-IAM-002/007). Access tokens are
 * stateless, so to cut one before its natural expiry we keep two Redis-backed
 * signals the auth filter consults on every request:
 *
 * <ul>
 *   <li><b>jti blacklist</b> — kills a single token (one device's logout).</li>
 *   <li><b>per-user invalidation epoch</b> — kills every token issued before a
 *       moment (user disabled, password reset, role revoked, logout-everywhere).</li>
 * </ul>
 *
 * All operations fail open: if Redis is unreachable they degrade to "not
 * revoked" rather than locking everyone out, and log a warning.
 */
public interface TokenGuardService {

    /** Blacklist a single access token by its {@code jti} until it would expire anyway. */
    void blacklistJti(String jti);

    boolean isJtiBlacklisted(String jti);

    /** Invalidate every access token for a user issued before now. */
    void invalidateUserTokens(Long userId);

    /** Epoch-second cutoff before which this user's tokens are invalid, or 0 if none. */
    long userInvalidatedAtEpoch(Long userId);
}
