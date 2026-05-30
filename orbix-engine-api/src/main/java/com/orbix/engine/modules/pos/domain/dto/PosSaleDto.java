package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.PosSaleLine;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PosSaleDto(
    Long id,
    String uid,
    String number,
    String clientOpId,
    Long tillSessionId,
    Long tillId,
    Long branchId,
    Long companyId,
    Long sectionId,
    Long customerId,
    Long cashierId,
    Long supervisorId,
    PosSaleKind kind,
    Long refundedFromSaleId,
    Instant saleAt,
    Instant serverAt,
    LocalDate businessDate,
    BigDecimal subtotalAmount,
    BigDecimal discountAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    BigDecimal tenderedAmount,
    BigDecimal changeAmount,
    PosSaleStatus status,
    Instant voidedAt,
    Long voidedBy,
    String voidReason,
    String notes,

    /**
     * Mirror of fiscal_receipt.status — null when no fiscalization attempted.
     * PROVISIONAL = awaiting EFDMS; FISCALIZED = QR/verification code available.
     * STUB values: pending TRA EFDMS spec confirmation for exact semantics.
     */
    String fiscalStatus,

    /**
     * STUB: pending TRA EFDMS spec confirmation — TRA verification code for receipt reprint.
     * Non-null only when fiscalStatus=FISCALIZED.
     */
    String fiscalVerificationCode,

    /**
     * STUB: pending TRA EFDMS spec confirmation — QR code payload for the POS to render.
     * Non-null only when fiscalStatus=FISCALIZED.
     */
    String fiscalQrPayload,

    List<PosSaleLineDto> lines,
    List<PosPaymentDto> payments
) {
    public static PosSaleDto from(PosSale s, List<PosSaleLine> lines, List<PosPayment> payments) {
        return new PosSaleDto(
            s.getId(), s.getUid(), s.getNumber(), s.getClientOpId(),
            s.getTillSessionId(), s.getTillId(), s.getBranchId(), s.getCompanyId(),
            s.getSectionId(), s.getCustomerId(), s.getCashierId(), s.getSupervisorId(),
            s.getKind(), s.getRefundedFromSaleId(),
            s.getSaleAt(), s.getServerAt(), s.getBusinessDate(),
            s.getSubtotalAmount(), s.getDiscountAmount(), s.getTaxAmount(),
            s.getTotalAmount(), s.getTenderedAmount(), s.getChangeAmount(),
            s.getStatus(), s.getVoidedAt(), s.getVoidedBy(), s.getVoidReason(),
            s.getNotes(),
            s.getFiscalStatus(), s.getFiscalVerificationCode(), s.getFiscalQrPayload(),
            lines.stream().map(PosSaleLineDto::from).toList(),
            payments.stream().map(PosPaymentDto::from).toList()
        );
    }
}
