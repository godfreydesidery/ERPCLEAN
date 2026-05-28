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
    public static final String UOM_MANAGE   = "UOM.MANAGE";

    public static final String PRICE_LIST_CREATE  = "PRICE_LIST.CREATE";
    public static final String PRICE_LIST_UPDATE  = "PRICE_LIST.UPDATE";
    public static final String PRICE_LIST_ARCHIVE = "PRICE_LIST.ARCHIVE";
    public static final String PRICE_SET          = "PRICE.SET";
    public static final String PRICE_APPROVE      = "PRICE.APPROVE";

    // ---- party -------------------------------------------------------------
    public static final String CUSTOMER_CREATE     = "CUSTOMER.CREATE";
    public static final String CUSTOMER_UPDATE     = "CUSTOMER.UPDATE";
    public static final String CUSTOMER_ARCHIVE    = "CUSTOMER.ARCHIVE";
    public static final String SUPPLIER_CREATE     = "SUPPLIER.CREATE";
    public static final String SUPPLIER_UPDATE     = "SUPPLIER.UPDATE";
    public static final String SUPPLIER_ARCHIVE    = "SUPPLIER.ARCHIVE";
    public static final String EMPLOYEE_CREATE     = "EMPLOYEE.CREATE";
    public static final String EMPLOYEE_UPDATE     = "EMPLOYEE.UPDATE";
    public static final String EMPLOYEE_ARCHIVE    = "EMPLOYEE.ARCHIVE";
    public static final String SALES_AGENT_CREATE  = "SALES_AGENT.CREATE";
    public static final String SALES_AGENT_UPDATE  = "SALES_AGENT.UPDATE";
    public static final String SALES_AGENT_ARCHIVE = "SALES_AGENT.ARCHIVE";

    // ---- day ---------------------------------------------------------------
    public static final String DAY_OPEN           = "DAY.OPEN";
    public static final String DAY_CLOSE          = "DAY.CLOSE";
    public static final String DAY_OVERRIDE       = "DAY.OVERRIDE";
    public static final String DAY_READ           = "DAY.READ";
    public static final String DAY_OVERRIDE_LIST  = "DAY.OVERRIDE_LIST";

    // ---- stock -------------------------------------------------------------
    public static final String STOCK_OVERSELL             = "STOCK.OVERSELL";
    public static final String STOCK_COUNT                = "STOCK.COUNT";
    public static final String STOCK_TRANSFER             = "STOCK.TRANSFER";
    public static final String STOCK_BATCH                = "STOCK.BATCH";
    public static final String STOCK_ADJUST               = "STOCK.ADJUST";
    public static final String STOCK_ADJUST_APPROVE       = "STOCK.ADJUST_APPROVE";
    public static final String STOCK_INTERNAL_CONSUMPTION = "STOCK.INTERNAL_CONSUMPTION";
    public static final String STOCK_COUNT_APPROVE        = "STOCK.COUNT_APPROVE";

    // ---- cash (Slice D — granular codes; coarse CASH.READ/ADJUST/BANKING
    //            stay seeded as group-grants but new write endpoints require
    //            the per-aggregate code) -------------------------------------
    public static final String CASH_READ                    = "CASH.READ";
    public static final String CASH_ADJUST                  = "CASH.ADJUST";
    public static final String CASH_BANKING                 = "CASH.BANKING";
    public static final String CASH_ENTRY_READ              = "CASH.ENTRY.READ";
    public static final String CASH_BOOK_READ               = "CASH.BOOK.READ";
    public static final String CASH_ADJUSTMENT_POST         = "CASH.ADJUSTMENT.POST";
    public static final String CASH_ADJUSTMENT_ARCHIVE      = "CASH.ADJUSTMENT.ARCHIVE";
    public static final String CASH_BANK_DEPOSIT_POST       = "CASH.BANK_DEPOSIT.POST";
    public static final String CASH_BANK_DEPOSIT_ARCHIVE    = "CASH.BANK_DEPOSIT.ARCHIVE";

    // ---- pos (Slice D — granular read codes alongside the existing write
    //          codes; writes stay on POS.CASH_PICKUP / POS.PETTY_CASH) -------
    public static final String POS_CASH_PICKUP        = "POS.CASH_PICKUP";
    public static final String POS_PETTY_CASH         = "POS.PETTY_CASH";
    public static final String POS_CASH_PICKUP_READ   = "POS.CASH_PICKUP.READ";
    public static final String POS_PETTY_CASH_READ    = "POS.PETTY_CASH.READ";

    // ---- procurement (Slice B — LPO + GRN hardening) ----------------------
    public static final String PROCUREMENT_MANAGE_LPO        = "PROCUREMENT.MANAGE_LPO";
    public static final String PROCUREMENT_MANAGE_LPO_READ   = "PROCUREMENT.MANAGE_LPO.READ";
    public static final String PROCUREMENT_APPROVE_LPO       = "PROCUREMENT.APPROVE_LPO";
    public static final String PROCUREMENT_CANCEL_LPO        = "PROCUREMENT.CANCEL_LPO";
    public static final String PROCUREMENT_MANAGE_INVOICE    = "PROCUREMENT.MANAGE_INVOICE";
    public static final String GRN_POST                      = "GRN.POST";
    public static final String GRN_DIRECT                    = "GRN.DIRECT";
    public static final String GRN_READ                      = "GRN.READ";
    public static final String GRN_CANCEL                    = "GRN.CANCEL";

    // ---- sales (Slice C — invoice + receipt + AR-summary hardening) -------
    public static final String SALES_MANAGE_INVOICE          = "SALES.MANAGE_INVOICE";
    public static final String SALES_DISCOUNT_APPROVE        = "SALES.DISCOUNT_APPROVE";
    public static final String SALES_MANAGE_RECEIPT          = "SALES.MANAGE_RECEIPT";
    public static final String SALES_MANAGE_RETURN           = "SALES.MANAGE_RETURN";
    public static final String SALES_MANAGE_PACKING          = "SALES.MANAGE_PACKING";
    public static final String SALES_INVOICE_OVERRIDE_CREDIT = "SALES_INVOICE.OVERRIDE_CREDIT";
    public static final String SALES_INVOICE_REPRINT         = "SALES_INVOICE.REPRINT";
    public static final String SALES_REPORT_AR_SUMMARY       = "SALES.REPORT.AR_SUMMARY";

    // ---- debt (Slice G — standalone debt module, ADR-0005) ----------------
    public static final String DEBT_READ                = "DEBT.READ";
    public static final String DEBT_NOTE_CREATE         = "DEBT.NOTE.CREATE";
    public static final String DEBT_NOTE_ARCHIVE        = "DEBT.NOTE.ARCHIVE";
    public static final String DEBT_CREDIT_LIMIT_UPDATE = "DEBT.CREDIT_LIMIT.UPDATE";

    // ---- debt write-off (Slice G.2 — dual-approval AR + AP write-off) ----
    public static final String DEBT_WRITE_OFF_REQUEST = "DEBT.WRITE_OFF.REQUEST";
    public static final String DEBT_WRITE_OFF_APPROVE = "DEBT.WRITE_OFF.APPROVE";

    // ---- vendor returns (Slice H.1 — vendor return + credit-note allocation) ----
    public static final String PROCUREMENT_MANAGE_RETURN = "PROCUREMENT.MANAGE_RETURN";
}
