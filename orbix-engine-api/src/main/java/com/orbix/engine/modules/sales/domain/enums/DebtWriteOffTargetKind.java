package com.orbix.engine.modules.sales.domain.enums;

/**
 * Discriminator for a {@link com.orbix.engine.modules.sales.domain.entity.DebtWriteOff}:
 * whether the write-off targets an AR (customer) or AP (supplier) invoice.
 */
public enum DebtWriteOffTargetKind {
    CUSTOMER_INVOICE,
    SUPPLIER_INVOICE
}
