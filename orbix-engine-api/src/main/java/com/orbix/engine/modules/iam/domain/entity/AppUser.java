package com.orbix.engine.modules.iam.domain.entity;

import com.orbix.engine.modules.common.domain.entity.UidEntity;
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
@Table(name = "app_user", uniqueConstraints = {
    @UniqueConstraint(name = "uk_app_user_username", columnNames = "username"),
    @UniqueConstraint(name = "uk_app_user_uid", columnNames = "uid")
})
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id", callSuper = false)
@ToString(exclude = { "passwordHash" })
public class AppUser extends UidEntity {

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

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    /**
     * Forces the user to set a new password on their next login. Flipped on
     * by admin-issued create / reset-password flows; flipped off by the
     * self-service change-password endpoint.
     */
    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword = false;

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
        this.lastFailedLoginAt = null;
        this.updatedAt = at;
    }

    public void recordFailedLogin(Instant at, int lockoutThreshold,
                                  Duration baseLock, Duration maxLock, Duration failWindow) {
        // Quiet-period decay: only a full window with no failures resets the
        // streak. We deliberately do NOT reset merely because a prior lockout
        // elapsed — that's what lets the penalty escalate on a sustained burst.
        // (login() never calls this while still locked, so lockedUntil here is
        // always null or already in the past.)
        boolean windowExpired = lastFailedLoginAt == null || at.isAfter(lastFailedLoginAt.plus(failWindow));
        if (windowExpired) {
            this.failedLoginCount = 0;
            this.lockedUntil = null;
        }
        this.failedLoginCount++;
        this.lastFailedLoginAt = at;
        if (failedLoginCount >= lockoutThreshold) {
            // Exponential backoff: base, 2·base, 4·base … capped at maxLock.
            this.lockedUntil = at.plus(exponentialBackoff(baseLock, maxLock, failedLoginCount - lockoutThreshold));
        }
        this.updatedAt = at;
    }

    /** {@code base * 2^step}, overflow-guarded and capped at {@code max}. */
    private static Duration exponentialBackoff(Duration base, Duration max, int step) {
        if (step >= 32) return max;
        Duration scaled = base.multipliedBy(1L << step);
        return scaled.compareTo(max) > 0 ? max : scaled;
    }

    /** Admin-issued password reset — flips the must-change flag. */
    public void resetPassword(String newPasswordHash, boolean mustChange, Long actorId) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = mustChange;
        touch(actorId);
    }

    /** Self-service password change — clears the must-change flag. */
    public void changePassword(String newPasswordHash, Long actorId) {
        this.passwordHash = newPasswordHash;
        this.mustChangePassword = false;
        touch(actorId);
    }

    public void updateProfile(String displayName, String email, String phone,
                              Long defaultBranchId, Long actorId) {
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.defaultBranchId = defaultBranchId;
        touch(actorId);
    }

    public void setStatus(AppUserStatus next, Long actorId) {
        this.status = next;
        if (next == AppUserStatus.ACTIVE) {
            this.lockedUntil = null;
            this.failedLoginCount = 0;
            this.lastFailedLoginAt = null;
        }
        touch(actorId);
    }

    public void unlock(Long actorId) {
        this.lockedUntil = null;
        this.failedLoginCount = 0;
        this.lastFailedLoginAt = null;
        touch(actorId);
    }

    private void touch(Long actorId) {
        this.updatedAt = Instant.now();
        this.updatedBy = actorId;
    }
}
