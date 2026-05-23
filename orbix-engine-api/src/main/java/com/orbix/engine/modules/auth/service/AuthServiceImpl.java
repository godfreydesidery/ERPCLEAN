package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.auth.domain.dto.LoginRequestDto;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.auth.domain.entity.RefreshToken;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.auth.repository.RefreshTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final int LOCKOUT_THRESHOLD = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);
    // Rolling window over which failed attempts accumulate. Once a user goes
    // this long without a failure, the counter decays so stale misses can't
    // compound into an instant lockout weeks later.
    private static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(15);
    private static final SecureRandom RNG = new SecureRandom();

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final PermissionResolverService permissions;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    /** A real bcrypt hash to verify against when no/locked user matches, so a
     *  missing or locked account costs the same time as a real password check
     *  (defeats username enumeration by response timing). */
    private final String dummyHash;

    public AuthServiceImpl(AppUserRepository users,
                           RefreshTokenRepository refreshTokens,
                           PasswordEncoder passwords,
                           JwtService jwt,
                           PermissionResolverService permissions,
                           @Value("${orbix.jwt.access-ttl}") Duration accessTtl,
                           @Value("${orbix.jwt.refresh-ttl}") Duration refreshTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.permissions = permissions;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.dummyHash = passwords.encode(new BigInteger(130, RNG).toString(32));
    }

    @Override
    // noRollbackFor: the failed-login path writes (increments the lockout
    // counter) and THEN throws. Default rollback-on-RuntimeException would
    // discard that write, so the counter never advanced and lockout never
    // engaged. Keep the increment committed while still surfacing the 401.
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public LoginResponseDto login(LoginRequestDto request) {
        AppUser user = users.findByUsername(request.username()).orElse(null);
        Instant now = Instant.now();

        if (user == null || !user.canLogIn(now)) {
            // Spend a bcrypt verify against a throwaway hash so a missing or
            // locked account isn't distinguishable from a wrong password by
            // response timing (username enumeration defence).
            passwords.matches(request.password(), dummyHash);
            throw new InvalidCredentialsException();
        }

        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now, LOCKOUT_THRESHOLD, LOCKOUT_DURATION, FAILED_LOGIN_WINDOW);
            users.save(user);
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin(now);
        users.save(user);

        return issueTokens(user);
    }

    @Override
    // noRollbackFor: the theft branch revokes all of the user's tokens and THEN
    // throws. Default rollback-on-RuntimeException would undo the revocation,
    // defeating reuse-detection entirely. Commit the revoke, still return 401.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponseDto refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        String hash = hash(rawRefreshToken);

        RefreshToken stored = refreshTokens.findByTokenHash(hash)
            .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.getRevokedAt() != null) {
            // True theft signal: a single-use token presented again after it was
            // already rotated/revoked. Burn every refresh token for this user.
            refreshTokens.revokeAllForUser(stored.getUserId(), now);
            log.warn("Refresh token reuse detected for user {} — revoked all tokens", stored.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (!stored.isUsable(now)) {
            // Not revoked but unusable ⇒ simply expired. Benign — reject without
            // nuking the user's other sessions or crying theft.
            throw new InvalidRefreshTokenException();
        }

        AppUser user = users.findById(stored.getUserId())
            .orElseThrow(InvalidRefreshTokenException::new);
        if (!user.canLogIn(now)) {
            throw new InvalidRefreshTokenException();
        }

        stored.revoke(now);
        refreshTokens.save(stored);

        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = hash(rawRefreshToken);
        refreshTokens.findByTokenHash(hash).ifPresent(t -> {
            t.revoke(Instant.now());
            refreshTokens.save(t);
        });
    }

    @Override
    @Transactional
    public void logoutEverywhere(Long userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
    }

    @Override
    @Transactional
    public LoginResponseDto reissueTokens(Long userId) {
        AppUser user = users.findById(userId).orElseThrow(InvalidCredentialsException::new);
        if (!user.canLogIn(Instant.now())) {
            throw new InvalidCredentialsException();
        }
        return issueTokens(user);
    }

    private LoginResponseDto issueTokens(AppUser user) {
        Long companyId = user.getDefaultCompanyId() == null ? 0L : user.getDefaultCompanyId();
        Long branchId = user.getDefaultBranchId();
        List<String> perms = List.copyOf(permissions.resolve(user.getId(), companyId, branchId));
        String accessToken = jwt.issueAccessToken(user.getId(), companyId, branchId, perms);

        String rawRefresh = generateOpaqueToken();
        Instant now = Instant.now();
        refreshTokens.save(new RefreshToken(
            user.getId(),
            hash(rawRefresh),
            now.plus(refreshTtl),
            null
        ));

        return new LoginResponseDto(
            accessToken,
            rawRefresh,
            "Bearer",
            accessTtl.toSeconds(),
            new LoginResponseDto.UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDefaultCompanyId(),
                user.getDefaultBranchId(),
                user.isMustChangePassword()
            )
        );
    }

    private String generateOpaqueToken() {
        byte[] bytes = new byte[32];  // 256 bits
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
