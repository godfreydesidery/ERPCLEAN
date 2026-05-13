package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.auth.domain.dto.LoginRequestDto;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;

public interface AuthService {

    /**
     * Verify credentials and issue an access token. On wrong password the
     * implementation increments the failed-login counter and may lock the
     * account; on success it clears the counter and stamps last_login_at.
     *
     * @throws InvalidCredentialsException if username unknown, password
     *         wrong, account inactive, or account currently locked.
     */
    LoginResponseDto login(LoginRequestDto request);

    class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }
}
