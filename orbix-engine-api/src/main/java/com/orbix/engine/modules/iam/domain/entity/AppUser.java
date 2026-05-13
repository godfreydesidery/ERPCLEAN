package com.orbix.engine.modules.iam.domain.entity;

import com.orbix.engine.modules.iam.domain.enums.AppUserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.time.Duration;
import java.time.Instant;

/**
 * Authenticated principal. Maps to {@code app_user} (DATA-MODEL.md §1.4).
 * Roles and permissions are resolved separately via {@code user_role} +
 * {@code role_permission}.
 */
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(name = "uk_app_user_username", columnNames = "username"))
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
@ToString(exclude = { "passwordHash" })
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "app_user_seq")
    @SequenceGenerator(name = "app_user_seq", sequenceName = "app_user_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 120)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 120)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(length = 40)
    private String phone;

    @Column(name = "default_company_id")
    private Long defaultCompanyId;

    @Column(name = "default_branch_id")
    private Long defaultBranchId;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AppUserStatus status = AppUserStatus.ACTIVE;

    @Version
    private Integer version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    public AppUser(String username, String passwordHash, String displayName,
            Long defaultCompanyId, Long defaultBranchId, Long actorId) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.defaultCompanyId = defaultCompanyId;
        this.defaultBranchId = defaultBranchId;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        this.createdBy = actorId;
        this.updatedBy = actorId;
    }

    public boolean canLogIn(Instant now) {
        if (status != AppUserStatus.ACTIVE)
            return false;
        return lockedUntil == null || now.isAfter(lockedUntil);
    }

    public void recordSuccessfulLogin(Instant at) {
        this.lastLoginAt = at;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
        this.updatedAt = at;
    }

    public void recordFailedLogin(Instant at, int lockoutThreshold, Duration lockoutFor) {
        this.failedLoginCount++;
        if (failedLoginCount >= lockoutThreshold) {
            this.lockedUntil = at.plus(lockoutFor);
        }
        this.updatedAt = at;
    }
}
