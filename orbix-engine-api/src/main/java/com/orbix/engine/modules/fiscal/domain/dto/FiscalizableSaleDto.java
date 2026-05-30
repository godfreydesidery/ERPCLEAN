package com.orbix.engine.modules.fiscal.domain.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Input to FiscalProvider.fiscalize — a snapshot of the POS sale data needed
 * to build a TRA fiscal receipt. Constructed by FiscalizationServiceImpl from
 * the pos_sale + pos_sale_line rows fetched after the outbox event is consumed.
 *
 * <p>Fields are denoted STUB where their exact TRA semantics are unconfirmed.
 * The data model is derived from the ADR-0006 requirements section and public
 * TRA/VFD documentation — confirm against the official EFDMS spec before
 * sending to a live device.
 */
public record FiscalizableSaleDto(

    /** Internal POS sale id (used for correlation, not sent to EFDMS). */
    Long posSaleId,

    /** POS sale business number (e.g. "POS-0001-00123"). */
    String saleNumber,

    /** When the cashier finalized the sale on the till. */
    Instant saleAt,

    Long companyId,
    Long branchId,

    /** Seller TIN registered with TRA. STUB: exact field name/format pending spec. */
    String sellerTin,

    /** Seller VRN (VAT registration number). STUB: mandatory vs optional TBD. */
    String sellerVrn,

    /** Buyer TIN — optional for walk-in cash retail; required B2B. STUB: rules pending. */
    String buyerTin,

    /** Buyer VRN — optional. STUB: rules pending. */
    String buyerVrn,

    /** Line items with VAT breakdown. */
    List<LineDto> lines,

    /** Total amount excl. VAT (functional currency / TZS). */
    BigDecimal totalExclTax,

    /** Total VAT amount. */
    BigDecimal totalTax,

    /** Grand total incl. VAT. */
    BigDecimal totalInclTax,

    /** Functional currency code (should be "TZS" for TZ-VFD). */
    String currencyCode

) {

    /**
     * One line item on a fiscal receipt.
     *
     * <p>STUB: TRA VAT tax codes (A=standard 18%, B=special, C=zero, D=exempt)
     * are unconfirmed — taxCode here is a placeholder derived from the VAT group rate.
     */
    public record LineDto(
        int lineNo,
        String itemCode,
        String itemDescription,
        BigDecimal qty,
        BigDecimal unitPrice,
        BigDecimal discountAmount,
        BigDecimal netAmount,

        /** STUB: pending TRA EFDMS spec — exact VAT tax code values (A/B/C/D or enum). */
        String taxCode,

        BigDecimal taxAmount,
        BigDecimal lineTotal
    ) {}
}
