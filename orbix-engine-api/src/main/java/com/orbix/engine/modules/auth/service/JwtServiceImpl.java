package com.orbix.engine.modules.auth.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
public class JwtServiceImpl implements JwtService {

    @Value("${orbix.jwt.issuer}") private String issuer;
    @Value("${orbix.jwt.access-ttl}") private Duration accessTtl;
    @Value("${orbix.jwt.signing-mode}") private String signingMode;

    private SecretKey signingKey;

    @PostConstruct
    void init() {
        // Local-dev only: generate an in-memory key per process.
        // Production: load RS256 keys from a secret store; do not commit secrets.
        this.signingKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    }

    @Override
    public String issueAccessToken(long userId, long companyId, Long branchId, List<String> privileges) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(Long.toString(userId))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .claims(Map.of(
                "uid", userId,
                "cid", companyId,
                "bid", branchId == null ? -1L : branchId,
                "privs", privileges
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
            c.get("uid", Long.class),
            c.get("cid", Long.class),
            bid == null || bid < 0 ? null : bid,
            (List<String>) c.get("privs", List.class)
        );
    }
}
