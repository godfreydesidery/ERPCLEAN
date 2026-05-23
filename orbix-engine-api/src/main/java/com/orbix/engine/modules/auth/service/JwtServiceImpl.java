package com.orbix.engine.modules.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    /** The only signing mode currently implemented. */
    private static final String DEV_IN_MEMORY = "dev-in-memory";

    @Value("${orbix.jwt.issuer}") private String issuer;
    @Value("${orbix.jwt.access-ttl}") private Duration accessTtl;
    @Value("${orbix.jwt.signing-mode}") private String signingMode;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Fail fast rather than silently fall back: a deployment that asks for
        // RS256-from-secret-store must NOT quietly run on an ephemeral HS256 key
        // (every restart would invalidate all tokens, and the key isn't shared).
        if (!DEV_IN_MEMORY.equals(signingMode)) {
            throw new IllegalStateException(
                "Unsupported orbix.jwt.signing-mode '" + signingMode + "'. Only '"
                + DEV_IN_MEMORY + "' (ephemeral HS256) is implemented; RS256 signing "
                + "from a secret store is not wired yet (ARCHITECTURE.md §2.5/§8). "
                + "Refusing to start to avoid a silent insecure fallback.");
        }
        // Local-dev only: ephemeral in-memory key per process.
        this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        log.warn("JWT signing mode '{}': generated an ephemeral in-memory HS256 key. "
            + "Tokens do not survive a restart and the key is not shared across "
            + "instances — NOT for production.", DEV_IN_MEMORY);
    }

    @Override
    public String issueAccessToken(long userId, long companyId, Long branchId, List<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(Long.toString(userId))
            .id(java.util.UUID.randomUUID().toString())   // jti — for single-token revocation
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            // userId lives in the standard `sub` claim (set above). We do NOT
            // add a custom `uid` claim: project-wide `uid` means the Crockford
            // ULID external identifier, so reusing it for the numeric user id
            // is a confusing collision.
            .claims(Map.of(
                "cid", companyId,
                "bid", branchId == null ? -1L : branchId,
                "perms", permissions
            ))
            .signWith(signingKey)
            .compact();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Claims parse(String token) {
        io.jsonwebtoken.Claims c = Jwts.parser()
            .verifyWith(signingKey)
            .requireIssuer(issuer)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        Long bid = c.get("bid", Long.class);
        return new Claims(
            Long.valueOf(c.getSubject()),
            c.get("cid", Long.class),
            bid == null || bid < 0 ? null : bid,
            (List<String>) c.get("perms", List.class),
            c.getId(),
            c.getIssuedAt() == null ? null : c.getIssuedAt().toInstant()
        );
    }
}
