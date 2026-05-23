package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.config.BootstrapProperties;
import com.orbix.engine.modules.admin.domain.dto.FirstRunRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Env-driven first-run bootstrap. On startup, if {@code orbix.bootstrap.enabled}
 * and the database has no organisation, builds a {@link FirstRunRequestDto} from
 * {@link BootstrapProperties} and runs {@link FirstRunSetupService#bootstrap}.
 * Already-bootstrapped deployments are a no-op.
 *
 * <p>Fail-fast: when enabled, a missing / weak / placeholder admin password (or
 * blank org/company name) aborts startup so a guessable root can never ship.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapRunner implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final Set<String> WEAK_PASSWORDS = Set.of(
        "changeme", "change_me", "password", "rootadmin", "admin", "secret");

    private final BootstrapProperties props;
    private final FirstRunSetupService setup;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.enabled()) {
            return;
        }
        if (setup.isBootstrapped()) {
            log.info("Bootstrap: deployment already initialised — skipping env bootstrap");
            return;
        }
        validate();

        FirstRunRequestDto request = new FirstRunRequestDto(
            new FirstRunRequestDto.OrganisationInfoDto(
                props.org().name().trim(),
                blankToNull(props.org().legalName()),
                props.org().currency().trim().toUpperCase(),
                props.org().country().trim().toUpperCase()),
            new FirstRunRequestDto.CompanyInfoDto(
                props.company().code().trim(),
                props.company().name().trim(),
                props.company().timezone()),
            new FirstRunRequestDto.BranchInfoDto(
                props.branch().code().trim(),
                props.branch().name().trim(),
                props.company().timezone()),
            new FirstRunRequestDto.AdminUserDto(
                props.admin().username().trim(),
                props.admin().password(),
                props.admin().displayName().trim()));

        var result = setup.bootstrap(request);
        log.info("Bootstrap: initialised org={} company={} branch={} admin={} (company-wide, no default branch)",
            result.organisationId(), result.companyCode(), result.branchCode(), result.adminUsername());
    }

    /** Fail-fast on weak config so an empty/guessable root never reaches production. */
    private void validate() {
        String pwd = props.admin().password();
        if (pwd == null || pwd.length() < MIN_PASSWORD_LENGTH
                || WEAK_PASSWORDS.contains(pwd.toLowerCase())) {
            throw new IllegalStateException(
                "orbix.bootstrap.enabled=true but ORBIX_BOOTSTRAP_ADMIN_PASSWORD is missing or too weak "
                + "(min " + MIN_PASSWORD_LENGTH + " chars, not a common placeholder). Refusing to start.");
        }
        if (isBlank(props.org().name()) || isBlank(props.company().name())) {
            throw new IllegalStateException(
                "orbix.bootstrap.enabled=true but ORBIX_BOOTSTRAP_ORG_NAME / _COMPANY_NAME is blank. "
                + "Refusing to start.");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
