package com.orbix.engine.modules.iam.repository;

import com.orbix.engine.modules.iam.domain.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    List<UserRole> findByUserIdAndCompanyIdAndRevokedAtIsNull(Long userId, Long companyId);

    List<UserRole> findByRoleIdAndRevokedAtIsNull(Long roleId);

    boolean existsByRoleIdAndRevokedAtIsNull(Long roleId);

    /**
     * True when the user holds at least one active role grant that covers the
     * given branch in the given company — either a branch-specific grant or a
     * company-wide grant ({@code branch_id is null}).
     */
    @Query("""
        select count(ur) > 0 from UserRole ur
        where ur.userId = :userId
          and ur.companyId = :companyId
          and ur.revokedAt is null
          and (ur.branchId is null or ur.branchId = :branchId)
        """)
    boolean hasBranchAccess(@Param("userId") Long userId,
                            @Param("companyId") Long companyId,
                            @Param("branchId") Long branchId);

    /**
     * Returns the distinct permission codes granted to a user in a company
     * (and matching the requested branch, if any). Branch-scoped grants are
     * filtered when {@code branchId} is supplied; null-branch grants always
     * count.
     */
    @Query("""
        select distinct p.code from UserRole ur
        join Role r on r.id = ur.roleId
        join r.permissions p
        where ur.userId = :userId
          and ur.companyId = :companyId
          and ur.revokedAt is null
          and r.status = com.orbix.engine.modules.admin.domain.enums.AdminStatus.ACTIVE
          and (:branchId is null or ur.branchId is null or ur.branchId = :branchId)
        """)
    Set<String> findActivePermissionCodes(@Param("userId") Long userId,
                                          @Param("companyId") Long companyId,
                                          @Param("branchId") Long branchId);

    /**
     * True when the user holds at least one active grant whose {@code branch_id}
     * is NULL (company-wide). Used to decide if a caller can see all users in
     * the company or only those in their branch scope.
     */
    @Query("""
        select count(ur) > 0 from UserRole ur
        where ur.userId = :userId
          and ur.companyId = :companyId
          and ur.revokedAt is null
          and ur.branchId is null
        """)
    boolean hasAnyCompanyWideGrant(@Param("userId") Long userId,
                                   @Param("companyId") Long companyId);

    /**
     * True when the target user is "visible" to a branch-scoped admin:
     *   - target has at least one active grant covering this branch
     *     (specific or company-wide), OR
     *   - target has NO active grants yet (orphan, freshly created — needs
     *     someone to assign them roles).
     * Company-wide admins bypass this check entirely.
     */
    @Query("""
        select case when (
            exists (
                select 1 from UserRole ur
                where ur.userId = :userId
                  and ur.companyId = :companyId
                  and ur.revokedAt is null
                  and (ur.branchId is null or ur.branchId = :branchId)
            ) or not exists (
                select 1 from UserRole ur
                where ur.userId = :userId
                  and ur.companyId = :companyId
                  and ur.revokedAt is null
            )
        ) then true else false end
        """)
    boolean isUserVisibleInBranch(@Param("userId") Long userId,
                                  @Param("companyId") Long companyId,
                                  @Param("branchId") Long branchId);
}
