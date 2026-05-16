package com.orbix.engine.modules.iam.repository;

import com.orbix.engine.modules.iam.domain.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
