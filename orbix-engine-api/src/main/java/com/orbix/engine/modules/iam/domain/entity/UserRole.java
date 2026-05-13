package com.orbix.engine.modules.iam.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Assignment of a role to a user, scoped per company (and optionally per
 * branch). DATA-MODEL.md §1.8.
 */
@Entity
@Table(name = "user_role")
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class UserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_role_seq")
    @SequenceGenerator(name = "user_role_seq", sequenceName = "user_role_seq", allocationSize = 50)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** null = grant applies to every branch within the company. */
    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by", nullable = false)
    private Long grantedBy;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public UserRole(Long userId, Long roleId, Long companyId, Long branchId, Long grantedBy) {
        this.userId = userId;
        this.roleId = roleId;
        this.companyId = companyId;
        this.branchId = branchId;
        this.grantedAt = Instant.now();
        this.grantedBy = grantedBy;
    }

    public boolean isActive(Instant now) {
        return revokedAt == null;
    }

    public void revoke(Instant at) {
        if (revokedAt == null) this.revokedAt = at;
    }
}
