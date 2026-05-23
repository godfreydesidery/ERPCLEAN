package com.orbix.engine.modules.iam.service;

import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.Role;
import com.orbix.engine.modules.iam.domain.entity.UserRole;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.repository.RoleRepository;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Local-profile bootstrap. Seeds an organisation + company + branch + admin
 * user on an empty database so a developer can log in immediately.
 *
 * In production this is replaced by US-COMP-001 (first-run setup wizard).
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class DevSeed implements CommandLineRunner {

    private static final String DEV_USERNAME = "admin";
    private static final String DEV_PASSWORD = "orbix";

    private final JdbcTemplate jdbc;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwords;

    @Override
    @Transactional
    public void run(String... args) {
        if (users.existsByUsername(DEV_USERNAME)) {
            log.info("DevSeed: admin already present, skipping");
            return;
        }

        Instant now = Instant.now();
        Long systemActor = 0L;

        Long orgId = nextVal("organisation_seq");
        jdbc.update("""
            INSERT INTO organisation
              (id, name, legal_name, currency_code, country_code, status,
               created_at, updated_at, created_by, updated_by, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            orgId, "Orbix Dev", "Orbix Dev Ltd", "UGX", "UG", "ACTIVE",
            now, now, systemActor, systemActor);

        Long companyId = nextVal("company_seq");
        jdbc.update("""
            INSERT INTO company
              (id, organisation_id, code, name, currency_code, country_code, time_zone,
               status, created_at, updated_at, created_by, updated_by, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """,
            companyId, orgId, "DEV", "Dev Company", "UGX", "UG", "Africa/Kampala",
            "ACTIVE", now, now, systemActor, systemActor);

        Long branchId = nextVal("branch_seq");
        jdbc.update("""
            INSERT INTO branch
              (id, uid, company_id, code, name, type, time_zone, is_default, status,
               created_at, updated_at, created_by, updated_by, version)
            VALUES (?, ?, ?, ?, ?, ?, ?, TRUE, ?, ?, ?, ?, ?, 0)
            """,
            branchId, UidGenerator.next(), companyId, "HQ", "Head Office", "RETAIL",
            "Africa/Kampala", "ACTIVE", now, now, systemActor, systemActor);

        AppUser admin = new AppUser(
            DEV_USERNAME,
            passwords.encode(DEV_PASSWORD),
            "Administrator",
            companyId,
            branchId,
            systemActor
        );
        admin = users.save(admin);

        Role adminRole = roles.findByCode("ADMIN").orElseThrow(() ->
            new IllegalStateException("Seed ADMIN role missing — V4 migration did not run"));
        userRoles.save(new UserRole(admin.getId(), adminRole.getId(), companyId, null, admin.getId()));

        log.warn("============================================================");
        log.warn(" DevSeed created admin login — for LOCAL profile only");
        log.warn("   username: {}", DEV_USERNAME);
        log.warn("   password: {}", DEV_PASSWORD);
        log.warn("   company:  {} (id={})", "Dev Company", companyId);
        log.warn("   branch:   {} (id={})", "Head Office", branchId);
        log.warn("============================================================");
    }

    private Long nextVal(String sequenceName) {
        // DB-agnostic next-value lookup. MariaDB / SQL-standard: NEXT VALUE
        // FOR seq_name (no quotes — seq_name is an identifier). Postgres:
        // nextval('seq_name') (function call with a string literal). Try
        // MariaDB / standard form first, then Postgres.
        try {
            return jdbc.queryForObject(
                "SELECT NEXT VALUE FOR " + sequenceName, Long.class);
        } catch (Exception ignored) {
            return jdbc.queryForObject(
                "SELECT nextval('" + sequenceName + "')", Long.class);
        }
    }
}
