package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.auth.domain.dto.LoginRequestDto;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.auth.domain.dto.SessionDto;

import java.util.List;

public interface AuthService {

    /**
     * Verify credentials and issue a new access + refresh token pair.
     *
     * @throws InvalidCredentialsException if username unknown, password
     *         wrong, account inactive, or account currently locked.
     */
    LoginResponseDto login(LoginRequestDto request);

    /**
     * Rotate a refresh token: validate, revoke the supplied one, and issue a
     * fresh access + refresh pair. Reuse of an already-revoked token is
     * treated as theft and revokes every refresh token owned by that user.
     */
    LoginResponseDto refresh(String refreshToken);

    /** Revoke a single refresh token (sign-out from this device). */
    void logout(String refreshToken);

    /** Revoke every refresh token owned by the given user (sign-out everywhere). */
    void logoutEverywhere(Long userId);

    /** List the user's active sessions (non-revoked, unexpired refresh tokens). US-IAM-003. */
    List<SessionDto> listSessions(Long userId);

    /**
     * Issue a fresh access + refresh pair for an already-authenticated user.
     * Used when session context changes mid-flight (e.g. active-branch switch)
     * so that the new JWT carries the up-to-date {@code branchId} claim and
     * {@code perms[]} resolved against the new branch context.
     */
    LoginResponseDto reissueTokens(Long userId);

    class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }

    /** Thrown when a known account is currently locked out (failed-attempt threshold reached). */
    class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }

    class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() {
            super("Refresh token is invalid or expired");
        }
    }
}
