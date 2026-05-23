package com.orbix.engine.modules.common.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
public class TokenGuardServiceImpl implements TokenGuardService {

    private static final String JTI_KEY = "auth:bl:jti:";
    private static final String UINVAL_KEY = "auth:uinval:";

    private final StringRedisTemplate redis;
    /** Keys live at most one access-token lifetime — older tokens expire on their own. */
    private final Duration accessTtl;

    public TokenGuardServiceImpl(StringRedisTemplate redis,
                                 @Value("${orbix.jwt.access-ttl}") Duration accessTtl) {
        this.redis = redis;
        this.accessTtl = accessTtl;
    }

    @Override
    public void blacklistJti(String jti) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        try {
            redis.opsForValue().set(JTI_KEY + jti, "1", accessTtl);
        } catch (Exception e) {
            log.warn("Redis unavailable — could not blacklist jti {}", jti, e);
        }
    }

    @Override
    public boolean isJtiBlacklisted(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(JTI_KEY + jti));
        } catch (Exception e) {
            log.warn("Redis unavailable — failing open on jti blacklist check", e);
            return false;
        }
    }

    @Override
    public void invalidateUserTokens(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redis.opsForValue().set(UINVAL_KEY + userId,
                Long.toString(Instant.now().getEpochSecond()), accessTtl);
        } catch (Exception e) {
            log.warn("Redis unavailable — could not invalidate tokens for user {}", userId, e);
        }
    }

    @Override
    public long userInvalidatedAtEpoch(Long userId) {
        if (userId == null) {
            return 0L;
        }
        try {
            String v = redis.opsForValue().get(UINVAL_KEY + userId);
            return v == null ? 0L : Long.parseLong(v);
        } catch (Exception e) {
            log.warn("Redis unavailable — failing open on user-invalidation check", e);
            return 0L;
        }
    }
}
