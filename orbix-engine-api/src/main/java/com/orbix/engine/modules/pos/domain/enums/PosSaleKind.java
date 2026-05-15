package com.orbix.engine.modules.pos.domain.enums;

/**
 * POS-sale flavour. SALE = ordinary till sale; REFUND is the F5.5 refund flow;
 * NO_SALE opens the cash drawer without a transaction (audited).
 */
public enum PosSaleKind {
    SALE,
    REFUND,
    NO_SALE
}
