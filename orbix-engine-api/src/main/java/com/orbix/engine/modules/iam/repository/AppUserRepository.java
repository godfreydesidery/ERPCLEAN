package com.orbix.engine.modules.iam.repository;

import com.orbix.engine.modules.iam.domain.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    Optional<AppUser> findByUid(String uid);

    boolean existsByUsername(String username);

    /**
     * F0.4c — every user belonging to a company, oldest first so admin can
     * scan the roster top-down. Includes INACTIVE / LOCKED / SUSPENDED rows
     * so the admin screen can show + manage them.
     */
    List<AppUser> findByDefaultCompanyIdOrderByIdAsc(Long defaultCompanyId);

    /**
     * Server-side paginated + searchable + status-filtered user listing. One
     * query for both scopes: company-wide callers ({@code companyWide = true})
     * see everyone in the company; branch-scoped callers see only users whose
     * home branch ({@code defaultBranchId}) is their active branch AND who are
     * not company-wide-privileged — users in other branches, users with no home
     * branch, and any user holding a company-wide grant are all hidden.
     * {@code q} matches username / display name / email (case-insensitive);
     * {@code statusFilter} is one of all|active|disabled|locked|reset.
     */
    @Query(value = """
        select u from AppUser u
        where u.defaultCompanyId = :companyId
          and (:companyWide = true
               or (u.defaultBranchId = :branchId
                   and not exists (select 1 from UserRole ur where ur.userId = u.id
                                     and ur.companyId = :companyId and ur.revokedAt is null
                                     and ur.branchId is null)))
          and (:q is null
               or lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.displayName) like lower(concat('%', :q, '%'))
               or (u.email is not null and lower(u.email) like lower(concat('%', :q, '%'))))
          and (:statusFilter = 'all'
               or (:statusFilter = 'active' and u.status = com.orbix.engine.modules.iam.domain.enums.AppUserStatus.ACTIVE)
               or (:statusFilter = 'disabled' and u.status <> com.orbix.engine.modules.iam.domain.enums.AppUserStatus.ACTIVE)
               or (:statusFilter = 'locked' and u.lockedUntil is not null and u.lockedUntil > :now)
               or (:statusFilter = 'reset' and u.mustChangePassword = true))
        """,
        countQuery = """
        select count(u) from AppUser u
        where u.defaultCompanyId = :companyId
          and (:companyWide = true
               or (u.defaultBranchId = :branchId
                   and not exists (select 1 from UserRole ur where ur.userId = u.id
                                     and ur.companyId = :companyId and ur.revokedAt is null
                                     and ur.branchId is null)))
          and (:q is null
               or lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.displayName) like lower(concat('%', :q, '%'))
               or (u.email is not null and lower(u.email) like lower(concat('%', :q, '%'))))
          and (:statusFilter = 'all'
               or (:statusFilter = 'active' and u.status = com.orbix.engine.modules.iam.domain.enums.AppUserStatus.ACTIVE)
               or (:statusFilter = 'disabled' and u.status <> com.orbix.engine.modules.iam.domain.enums.AppUserStatus.ACTIVE)
               or (:statusFilter = 'locked' and u.lockedUntil is not null and u.lockedUntil > :now)
               or (:statusFilter = 'reset' and u.mustChangePassword = true))
        """)
    Page<AppUser> search(@Param("companyId") Long companyId,
                         @Param("companyWide") boolean companyWide,
                         @Param("branchId") Long branchId,
                         @Param("q") String q,
                         @Param("statusFilter") String statusFilter,
                         @Param("now") Instant now,
                         Pageable pageable);
}
