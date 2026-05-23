package com.orbix.engine.modules.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Env-driven first-run bootstrap ({@code ORBIX_BOOTSTRAP_*}). When
 * {@code enabled} and the database has no organisation, the app self-bootstraps
 * org + company + branch + the company-wide {@code rootadmin} on startup
 * (see {@code BootstrapRunner}), replacing the interactive {@code /setup} wizard.
 *
 * <p>{@code resetToken} is the shared secret for the public
 * {@code POST /api/v1/setup/reset-rootadmin-password} endpoint — blank disables
 * that endpoint. The admin password is never accepted from a request; both the
 * initial bootstrap and the reset re-read it from {@code admin.password} here.
 */
@ConfigurationProperties(prefix = "orbix.bootstrap")
public record BootstrapProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("") String resetToken,
    @DefaultValue Org org,
    @DefaultValue Company company,
    @DefaultValue Branch branch,
    @DefaultValue Admin admin
) {

    public record Org(
        @DefaultValue("") String name,
        @DefaultValue("") String legalName,
        @DefaultValue("TZS") String currency,
        @DefaultValue("TZ") String country
    ) {}

    public record Company(
        @DefaultValue("COM") String code,
        @DefaultValue("") String name,
        @DefaultValue("Africa/Dar_es_Salaam") String timezone
    ) {}

    public record Branch(
        @DefaultValue("HQ") String code,
        @DefaultValue("Head Office") String name
    ) {}

    /**
     * Only the password is configurable. Username + display name are fixed
     * constants ({@code RootAdmin.USERNAME} / {@code DISPLAY_NAME}) so the
     * break-glass account is unambiguous and protectable.
     */
    public record Admin(
        @DefaultValue("") String password
    ) {}
}
