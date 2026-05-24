package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FirstRunRequestDto;
import com.orbix.engine.modules.admin.domain.dto.FirstRunResponseDto;
import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.entity.Organisation;
import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.BranchType;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.admin.repository.OrganisationRepository;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.domain.entity.Role;
import com.orbix.engine.modules.iam.domain.entity.UserRole;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.repository.RoleRepository;
import com.orbix.engine.modules.iam.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirstRunSetupServiceImpl implements FirstRunSetupService {

    private static final Long SYSTEM_ACTOR = 0L;
    private static final String DEFAULT_TIMEZONE = "Africa/Dar_es_Salaam";

    private final OrganisationRepository organisations;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final SectionRepository sections;
    private final CurrencyRepository currencies;
    private final AppUserRepository users;
    private final RoleRepository roles;
    private final UserRoleRepository userRoles;
    private final PasswordEncoder passwords;

    @Override
    @Transactional(readOnly = true)
    public boolean isBootstrapped() {
        return organisations.count() > 0;
    }

    @Override
    @Transactional
    public FirstRunResponseDto bootstrap(FirstRunRequestDto request) {
        if (organisations.count() > 0) {
            throw new AlreadyBootstrappedException("System is already initialised");
        }
        if (users.existsByUsername(request.admin().username())) {
            throw new AlreadyBootstrappedException("Admin username already exists");
        }

        FirstRunRequestDto.OrganisationInfoDto orgInfo = request.organisation();
        FirstRunRequestDto.CompanyInfoDto companyInfo = request.company();
        FirstRunRequestDto.BranchInfoDto branchInfo = request.branch();
        FirstRunRequestDto.AdminUserDto adminInfo = request.admin();

        Currency functional = currencies.findById(orgInfo.currencyCode())
            .orElseGet(() -> currencies.save(new Currency(
                orgInfo.currencyCode(),
                orgInfo.currencyCode(),
                orgInfo.currencyCode(),
                2
            )));
        functional.setStatus(AdminStatus.ACTIVE);

        Organisation organisation = organisations.save(new Organisation(
            orgInfo.name(),
            orgInfo.legalName(),
            orgInfo.currencyCode(),
            orgInfo.countryCode(),
            SYSTEM_ACTOR
        ));

        Company company = companies.save(new Company(
            organisation.getId(),
            companyInfo.code(),
            companyInfo.name(),
            orgInfo.currencyCode(),
            orgInfo.countryCode(),
            companyInfo.timeZone() != null ? companyInfo.timeZone() : DEFAULT_TIMEZONE,
            SYSTEM_ACTOR
        ));

        Branch branch = branches.save(new Branch(
            company.getId(),
            branchInfo.code(),
            branchInfo.name(),
            BranchType.RETAIL,
            branchInfo.timeZone() != null ? branchInfo.timeZone() : company.getTimeZone(),
            true,
            SYSTEM_ACTOR
        ));

        Section defaultSection = sections.save(new Section(
            branch.getId(),
            "MAIN",
            "Main Floor",
            SectionType.RETAIL_FLOOR,
            SYSTEM_ACTOR
        ));

        AppUser admin = new AppUser(
            adminInfo.username(),
            passwords.encode(adminInfo.password()),
            adminInfo.displayName(),
            company.getId(),
            // No default branch — the bootstrap admin (rootadmin) is company-wide.
            null,
            SYSTEM_ACTOR
        );
        admin = users.save(admin);

        Role adminRole = roles.findByCode("ADMIN").orElseThrow(() ->
            new IllegalStateException("Seed ADMIN role missing — V4 migration did not run"));
        // branchId = null -> the grant is company-wide (applies to every branch).
        userRoles.save(new UserRole(admin.getId(), adminRole.getId(), company.getId(), null, admin.getId()));

        log.info("First-run bootstrap completed — org={} company={} branch={} admin={} (company-wide)",
            organisation.getId(), company.getId(), branch.getId(), admin.getUsername());

        return new FirstRunResponseDto(
            organisation.getId(),
            company.getId(),
            company.getCode(),
            branch.getId(),
            branch.getCode(),
            defaultSection.getId(),
            admin.getId(),
            admin.getUsername()
        );
    }

    @Override
    @Transactional
    public void resetAdminPassword(String username, String rawPassword) {
        AppUser user = users.findByUsername(username)
            .orElseThrow(() -> new NoSuchElementException("Bootstrap admin not found: " + username));
        // Re-apply the env-sourced password. mustChange=false: it's ops-managed via env.
        user.resetPassword(passwords.encode(rawPassword), false, SYSTEM_ACTOR);
        users.save(user);
        log.info("Bootstrap admin password reset from env for user={}", username);
    }
}
