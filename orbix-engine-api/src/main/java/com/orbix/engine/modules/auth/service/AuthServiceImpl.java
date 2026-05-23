package com.orbix.engine.modules.auth.service;

import com.orbix.engine.modules.auth.domain.dto.LoginRequestDto;
import com.orbix.engine.modules.auth.domain.dto.LoginResponseDto;
import com.orbix.engine.modules.auth.domain.dto.SessionDto;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.auth.domain.entity.RefreshToken;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.auth.repository.RefreshTokenRepository;
import com.orbix.engine.modules.common.service.AuditLogWriter;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.TokenGuardService;
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
    // US-IAM-001: exponential lockout backoff — 1m, 2m, 4m … capped at 30m as
    // the failure streak grows past the threshold.
    private static final Duration LOCKOUT_BASE = Duration.ofMinutes(1);
    private static final Duration LOCKOUT_MAX = Duration.ofMinutes(30);
    // Rolling window over which failed attempts accumulate. A full window with
    // no failures decays the streak (and any escalation) back to zero.
    private static final Duration FAILED_LOGIN_WINDOW = Duration.ofHours(1);
    private static final SecureRandom RNG = new SecureRandom();

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final PermissionResolverService permissions;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final AuditLogWriter audit;
    private final RequestContext context;
    private final TokenGuardService tokenGuard;
    /** A real bcrypt hash to verify against when no/locked user matches, so a
     *  missing or locked account costs the same time as a real password check
     *  (defeats username enumeration by response timing). */
    private final String dummyHash;

    public AuthServiceImpl(AppUserRepository users,
                           RefreshTokenRepository refreshTokens,
                           PasswordEncoder passwords,
                           JwtService jwt,
                           PermissionResolverService permissions,
                           AuditLogWriter audit,
                           RequestContext context,
                           TokenGuardService tokenGuard,
                           @Value("${orbix.jwt.access-ttl}") Duration accessTtl,
                           @Value("${orbix.jwt.refresh-ttl}") Duration refreshTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.permissions = permissions;
        this.audit = audit;
        this.context = context;
        this.tokenGuard = tokenGuard;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.dummyHash = passwords.encode(new BigInteger(130, RNG).toString(32));
    }

    private static final String ENTITY = "AppUser";

    /** Build a small JSON meta blob: ip + client + one extra key/value. */
    private String authMeta(String extraKey, String extraVal) {
        return "{\"ip\":" + j(context.ip())
            + ",\"client\":" + j(context.clientVersion())
            + ",\"" + extraKey + "\":" + j(extraVal) + "}";
    }

    private static String j(String s) {
        return s == null ? "null" : "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean isLocked(AppUser user, Instant now) {
        return user.getLockedUntil() != null && now.isBefore(user.getLockedUntil());
    }

    private static String lockMessage(Instant now, Instant lockedUntil) {
        long secs = Math.max(0, Duration.between(now, lockedUntil).getSeconds());
        long mins = Math.max(1, (secs + 59) / 60);
        return "Account locked due to repeated failed sign-ins. Try again in about "
            + mins + " minute" + (mins == 1 ? "" : "s") + ", or ask an administrator to unlock it.";
    }

    @Override
    // noRollbackFor: the failed-login path writes (increments the lockout
    // counter) and THEN throws. Default rollback-on-RuntimeException would
    // discard that write, so the counter never advanced and lockout never
    // engaged. Keep the increment committed while still surfacing the error.
    @Transactional(noRollbackFor = { InvalidCredentialsException.class, AccountLockedException.class })
    public LoginResponseDto login(LoginRequestDto request) {
        AppUser user = users.findByUsername(request.username()).orElse(null);
        Instant now = Instant.now();

        if (user == null) {
            // Dummy bcrypt so a missing account isn't distinguishable from a
            // wrong password by response timing (username enumeration defence).
            passwords.matches(request.password(), dummyHash);
            audit.write(new AuditLogWriter.Record(
                0L, null, null, "LOGIN_FAILED", ENTITY, request.username(), null,
                authMeta("reason", "NO_SUCH_USER")));
            throw new InvalidCredentialsException();
        }

        // Locked: tell the legitimate user (distinct from a wrong password) so
        // they know to wait or contact an admin, instead of a confusing generic
        // "invalid credentials" while the right password keeps being rejected.
        if (isLocked(user, now)) {
            passwords.matches(request.password(), dummyHash);
            audit.write(new AuditLogWriter.Record(
                user.getId(), user.getDefaultCompanyId(), user.getDefaultBranchId(),
                "ACCOUNT_LOCKED", ENTITY, user.getId().toString(), null,
                authMeta("reason", "LOGIN_WHILE_LOCKED")));
            throw new AccountLockedException(lockMessage(now, user.getLockedUntil()));
        }

        if (!user.canLogIn(now)) {
            // Not locked (handled above) ⇒ inactive/suspended. Stay generic.
            passwords.matches(request.password(), dummyHash);
            audit.write(new AuditLogWriter.Record(
                user.getId(), user.getDefaultCompanyId(), user.getDefaultBranchId(),
                "LOGIN_FAILED", ENTITY, user.getId().toString(), null,
                authMeta("reason", "INACTIVE")));
            throw new InvalidCredentialsException();
        }

        if (!passwords.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin(now, LOCKOUT_THRESHOLD, LOCKOUT_BASE, LOCKOUT_MAX, FAILED_LOGIN_WINDOW);
            users.save(user);
            if (isLocked(user, now)) {
                // This attempt tripped the lockout — say so immediately.
                audit.write(new AuditLogWriter.Record(
                    user.getId(), user.getDefaultCompanyId(), user.getDefaultBranchId(),
                    "ACCOUNT_LOCKED", ENTITY, user.getId().toString(), null,
                    authMeta("reason", "THRESHOLD_REACHED")));
                throw new AccountLockedException(lockMessage(now, user.getLockedUntil()));
            }
            audit.write(new AuditLogWriter.Record(
                user.getId(), user.getDefaultCompanyId(), user.getDefaultBranchId(),
                "LOGIN_FAILED", ENTITY, user.getId().toString(), null,
                authMeta("reason", "BAD_CREDENTIALS")));
            throw new InvalidCredentialsException();
        }

        user.recordSuccessfulLogin(now);
        users.save(user);

        audit.write(new AuditLogWriter.Record(
            user.getId(), user.getDefaultCompanyId(), user.getDefaultBranchId(),
            "LOGIN", ENTITY, user.getId().toString(), null, authMeta("method", "PASSWORD")));
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
            tokenGuard.invalidateUserTokens(stored.getUserId());
            log.warn("Refresh token reuse detected for user {} — revoked all tokens", stored.getUserId());
            audit.write(new AuditLogWriter.Record(
                stored.getUserId(), null, null, "REFRESH_REUSE", ENTITY,
                stored.getUserId().toString(), null, authMeta("reason", "TOKEN_REUSE_REVOKED_ALL")));
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
            audit.write(new AuditLogWriter.Record(
                t.getUserId(), null, null, "LOGOUT", ENTITY,
                t.getUserId().toString(), null, authMeta("scope", "SESSION")));
        });
        // Kill the caller's current access token immediately, not just at expiry.
        tokenGuard.blacklistJti(context.jti());
    }

    @Override
    @Transactional
    public void logoutEverywhere(Long userId) {
        refreshTokens.revokeAllForUser(userId, Instant.now());
        tokenGuard.invalidateUserTokens(userId);
        audit.write(new AuditLogWriter.Record(
            userId, null, null, "LOGOUT_EVERYWHERE", ENTITY,
            userId.toString(), null, authMeta("scope", "ALL_SESSIONS")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionDto> listSessions(Long userId) {
        return refreshTokens
            .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByIssuedAtDesc(userId, Instant.now())
            .stream()
            .map(t -> new SessionDto(t.getId(), t.getClientInstallId(), t.getIssuedAt(), t.getExpiresAt()))
            .toList();
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
