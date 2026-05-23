package com.orbix.engine.modules.iam.domain.enums;

/**
 * Canonical permission code constants. Use these in {@code @PreAuthorize}
 * expressions and admin screens. Seeded into the {@code permission} table
 * by Flyway; the {@code ADMIN} system role is granted all of them on
 * first run.
 *
 * <p>Convention: {@code MODULE.ACTION}, all-caps with underscores in the
 * action segment. Module names match the back-end module directories.
 */
public final class Permissions {

    private Permissions() {}

    // ---- iam ---------------------------------------------------------------
    public static final String IAM_MANAGE_USERS  = "IAM.MANAGE_USERS";
    public static final String IAM_MANAGE_ROLES  = "IAM.MANAGE_ROLES";
    public static final String IAM_VIEW_AUDIT    = "IAM.VIEW_AUDIT";

    // ---- admin -------------------------------------------------------------
    public static final String ADMIN_MANAGE_BRANCHES   = "ADMIN.MANAGE_BRANCHES";
    public static final String ADMIN_MANAGE_SECTIONS   = "ADMIN.MANAGE_SECTIONS";
    public static final String ADMIN_MANAGE_CURRENCIES = "ADMIN.MANAGE_CURRENCIES";
    public static final String ADMIN_MANAGE_FX         = "ADMIN.MANAGE_FX";
    public static final String ADMIN_MANAGE_ROUTES     = "ADMIN.MANAGE_ROUTES";
    public static final String ADMIN_MANAGE_SETTINGS   = "ADMIN.MANAGE_SETTINGS";

    // ---- catalog -----------------------------------------------------------
    public static final String ITEM_CREATE  = "ITEM.CREATE";
    public static final String ITEM_UPDATE  = "ITEM.UPDATE";
    public static final String ITEM_ARCHIVE = "ITEM.ARCHIVE";

    // ---- party -------------------------------------------------------------
    public static final String PARTY_MANAGE_CUSTOMERS = "PARTY.MANAGE_CUSTOMERS";
    public static final String PARTY_MANAGE_SUPPLIERS = "PARTY.MANAGE_SUPPLIERS";
    public static final String PARTY_MANAGE_EMPLOYEES = "PARTY.MANAGE_EMPLOYEES";
    public static final String PARTY_MANAGE_AGENTS    = "PARTY.MANAGE_AGENTS";

    // ---- day ---------------------------------------------------------------
    public static final String DAY_OPEN     = "DAY.OPEN";
    public static final String DAY_CLOSE    = "DAY.CLOSE";
    public static final String DAY_OVERRIDE = "DAY.OVERRIDE";

    // ---- stock -------------------------------------------------------------
    public static final String STOCK_OVERSELL = "STOCK.OVERSELL";
    public static final String STOCK_COUNT    = "STOCK.COUNT";
    public static final String STOCK_TRANSFER = "STOCK.TRANSFER";
}
