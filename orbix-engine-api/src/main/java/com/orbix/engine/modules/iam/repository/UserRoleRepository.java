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
}
