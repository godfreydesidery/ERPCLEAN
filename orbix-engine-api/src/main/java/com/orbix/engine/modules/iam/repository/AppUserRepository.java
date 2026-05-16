package com.orbix.engine.modules.iam.repository;

import com.orbix.engine.modules.iam.domain.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * F0.4c — every user belonging to a company, oldest first so admin can
     * scan the roster top-down. Includes INACTIVE / LOCKED / SUSPENDED rows
     * so the admin screen can show + manage them.
     */
    List<AppUser> findByDefaultCompanyIdOrderByIdAsc(Long defaultCompanyId);

    /**
     * Users visible to a branch-scoped admin in {@code (companyId, branchId)} —
     * either holds an active grant covering that branch (specific or
     * company-wide), or has no grants at all yet (orphan, freshly created).
     * Company-wide admins should bypass this and use
     * {@link #findByDefaultCompanyIdOrderByIdAsc} to see everyone.
     */
    @Query("""
        select u from AppUser u
        where u.defaultCompanyId = :companyId
        and (
            exists (
                select 1 from UserRole ur
                where ur.userId = u.id
                  and ur.companyId = :companyId
                  and ur.revokedAt is null
                  and (ur.branchId is null or ur.branchId = :branchId)
            )
            or not exists (
                select 1 from UserRole ur
                where ur.userId = u.id
                  and ur.companyId = :companyId
                  and ur.revokedAt is null
            )
        )
        order by u.id asc
        """)
    List<AppUser> findVisibleInBranch(@Param("companyId") Long companyId,
                                      @Param("branchId") Long branchId);
}
