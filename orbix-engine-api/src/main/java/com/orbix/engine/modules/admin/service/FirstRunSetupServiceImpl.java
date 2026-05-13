package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.FirstRunRequestDto;
import com.orbix.engine.modules.admin.domain.dto.FirstRunResponseDto;
import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.entity.Company;
import com.orbix.engine.modules.admin.domain.entity.Currency;
import com.orbix.engine.modules.admin.domain.entity.Organisation;
import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.admin.repository.CompanyRepository;
import com.orbix.engine.modules.admin.repository.CurrencyRepository;
import com.orbix.engine.modules.admin.repository.OrganisationRepository;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.iam.domain.entity.AppUser;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirstRunSetupServiceImpl implements FirstRunSetupService {

    private static final Long SYSTEM_ACTOR = 0L;
    private static final String DEFAULT_TIMEZONE = "Africa/Kampala";

    private final OrganisationRepository organisations;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final SectionRepository sections;
    private final CurrencyRepository currencies;
    private final AppUserRepository users;
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
            "RETAIL",
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
            branch.getId(),
            SYSTEM_ACTOR
        );
        admin = users.save(admin);

        log.info("First-run bootstrap completed — org={} company={} branch={} admin={}",
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
}
