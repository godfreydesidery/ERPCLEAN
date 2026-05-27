package com.orbix.engine.modules.common.domain.enums;

import java.util.Optional;

/**
 * Registry of every globally-configurable default. Each constant carries its
 * stable storage key, type, UI grouping/label, and the compiled-in default
 * value used when no {@code app_setting} override exists. Defaults mirror the
 * previous {@code orbix.*} / @Value behaviour, so an un-overridden deployment
 * behaves exactly as before.
 */
public enum SettingKey {

    SALES_DISCOUNT_THRESHOLD_PCT("orbix.sales.discount-threshold-pct", SettingType.PERCENT, "Sales",
        "Discount approval threshold (%)",
        "Discount % above which supervisor approval is required.", "10"),

    PRICING_CHANGE_APPROVAL_PCT("orbix.pricing.change-approval-pct", SettingType.PERCENT, "Pricing",
        "Price change approval threshold (%)",
        "Absolute price change % above which an authoriser holding PRICE.APPROVE is required (0 disables).", "0"),

    STOCK_ADJUSTMENT_THRESHOLD("orbix.stock.adjustment-threshold", SettingType.MONEY, "Stock",
        "Stock adjustment approval threshold",
        "Adjustment value above which approval is required.", "50000"),

    PROCUREMENT_LPO_AUTO_APPROVAL("orbix.procurement.lpo-auto-approval-threshold", SettingType.MONEY, "Procurement",
        "LPO auto-approval threshold",
        "LPO total at or below which it is auto-approved (0 disables auto-approval).", "0"),

    PROCUREMENT_INVOICE_MATCH_TOLERANCE("orbix.procurement.invoice-match-tolerance-pct", SettingType.DECIMAL, "Procurement",
        "Invoice match tolerance (fraction)",
        "Allowed price/quantity variance as a fraction (0.005 = 0.5%).", "0.005"),

    POS_SESSION_VARIANCE_THRESHOLD("orbix.pos.session-variance-threshold", SettingType.MONEY, "POS",
        "Till session variance threshold",
        "Cash count variance above which a reason / approval is required.", "1000"),

    ORDERS_DEPOSIT_REQUIRED_PCT("orbix.orders.deposit-required-pct", SettingType.PERCENT, "Orders",
        "Deposit required (%)",
        "Minimum deposit % needed to confirm a customer order.", "30"),

    ORDERS_LAYBY_RESERVE_DAYS("orbix.orders.default-layby-reserve-days", SettingType.DAYS, "Orders",
        "Default layby reserve (days)",
        "Default stock reservation window for laybys.", "30"),

    ORDERS_PRE_ORDER_RESERVE_DAYS("orbix.orders.default-pre-order-reserve-days", SettingType.DAYS, "Orders",
        "Default pre-order reserve (days)",
        "Default stock reservation window for pre-orders.", "7"),

    ORDERS_CANCEL_REFUND_WINDOW_DAYS("orbix.orders.cancel-refund-window-days", SettingType.DAYS, "Orders",
        "Cancel/refund window (days)",
        "Days within which a cancelled order is auto-refunded.", "7"),

    PRODUCTION_YIELD_HARD_REJECT("orbix.production.yield-hard-reject-multiple", SettingType.DECIMAL, "Production",
        "Yield hard-reject multiple",
        "Reject a batch if actual yield exceeds expected by this multiple.", "2.0"),

    PRODUCTION_YIELD_SOFT_WARN("orbix.production.yield-soft-warn-fraction", SettingType.DECIMAL, "Production",
        "Yield soft-warn fraction",
        "Warn if actual yield deviates from expected by this fraction.", "0.5");

    private final String code;
    private final SettingType type;
    private final String group;
    private final String label;
    private final String description;
    private final String defaultValue;

    SettingKey(String code, SettingType type, String group, String label, String description, String defaultValue) {
        this.code = code;
        this.type = type;
        this.group = group;
        this.label = label;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    public String code() { return code; }
    public SettingType type() { return type; }
    public String group() { return group; }
    public String label() { return label; }
    public String description() { return description; }
    public String defaultValue() { return defaultValue; }

    public static Optional<SettingKey> byCode(String code) {
        for (SettingKey k : values()) {
            if (k.code.equals(code)) {
                return Optional.of(k);
            }
        }
        return Optional.empty();
    }
}
