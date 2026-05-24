package com.orbix.engine.modules.admin.domain.enums;

/**
 * What a branch is for. Drives provisioning: branches that trade with customers
 * ({@link #sellsToCustomers()}) auto-get a default sales section + a walk-in
 * customer on creation; storage/office branches don't. A {@link #WAREHOUSE}
 * only stores stock and supplies other branches via inter-branch transfers.
 *
 * <p>{@link #GENERAL} is the do-everything default — it sells (gets the floor +
 * walk-in) and, like every branch, holds stock; pick a specific type only when
 * you want the role made explicit.
 */
public enum BranchType {
    GENERAL(true),
    RETAIL(true),
    WHOLESALE(true),
    RESTAURANT(true),
    KIOSK(true),
    WAREHOUSE(false),
    HEAD_OFFICE(false),
    OTHER(false);

    private final boolean sellsToCustomers;

    BranchType(boolean sellsToCustomers) {
        this.sellsToCustomers = sellsToCustomers;
    }

    /** True when the branch trades from a floor — gets a default sales section + walk-in customer. */
    public boolean sellsToCustomers() {
        return sellsToCustomers;
    }
}
