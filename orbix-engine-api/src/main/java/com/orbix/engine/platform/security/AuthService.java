package com.orbix.engine.platform.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private static final int LOCKOUT_THRESHOLD = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final Duration accessTtl;

    public AuthService(AppUserRepository users,
                       PasswordEncoder passwords,
                       JwtService jwt,
                       @Value("${orbix.jwt.access-ttl}") Duration accessTtl) {
        this.users = users;
        this.passwords = passwords;
        this.jwt = jwt;
        this.accessTtl = accessTtl;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        AppUser user = users.findByUsername(request.username())
            .orElseThrow(() -> new InvalidCredentialsException());

        Instant now = Instant.now();
        if (!user.canLogIn(now)) {
            throw new InvalidCredentialsException();
        }

        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now, LOCKOUT_THRESHOLD, LOCKOUT_DURATION);
            users.save(user);
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin(now);
        users.save(user);

        String token = jwt.issueAccessToken(
            user.getId(),
            user.getDefaultCompanyId() == null ? 0L : user.getDefaultCompanyId(),
            user.getDefaultBranchId(),
            List.of()  // TODO: resolve from user_role / role_privilege once RBAC is wired
        );

        return new LoginResponse(
            token,
            "Bearer",
            accessTtl.toSeconds(),
            new LoginResponse.UserSummary(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDefaultCompanyId(),
                user.getDefaultBranchId()
            )
        );
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() {
            super("Invalid username or password");
        }
    }
}
