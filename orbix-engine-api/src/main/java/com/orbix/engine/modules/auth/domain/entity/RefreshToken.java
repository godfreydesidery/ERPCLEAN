package com.orbix.engine.modules.auth.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Instant;

/**
 * Single-use rotated refresh token. The raw token is never stored — only
 * its SHA-256 hash. DATA-MODEL.md §1.16.
 */
@Entity
@Table(name = "refresh_token")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"tokenHash"})
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "refresh_token_seq")
    @SequenceGenerator(name = "refresh_token_seq", sequenceName = "refresh_token_seq", allocationSize = 50)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 120)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "client_install_id", length = 80)
    private String clientInstallId;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public RefreshToken(Long userId, String tokenHash, Instant expiresAt, String clientInstallId) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.issuedAt = Instant.now();
        this.expiresAt = expiresAt;
        this.clientInstallId = clientInstallId;
    }

    public boolean isUsable(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    public void revoke(Instant at) {
        if (revokedAt == null) {
            this.revokedAt = at;
        }
    }
}
