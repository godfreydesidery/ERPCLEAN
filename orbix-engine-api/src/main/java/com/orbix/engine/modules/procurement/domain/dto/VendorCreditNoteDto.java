package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNote;
import com.orbix.engine.modules.procurement.domain.enums.VendorCreditNoteStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VendorCreditNoteDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long supplierId,
    Long vendorReturnId,
    LocalDate cnDate,
    String currencyCode,
    BigDecimal totalAmount,
    BigDecimal allocatedAmount,
    BigDecimal availableAmount,
    VendorCreditNoteStatus status,
    String notes,
    List<VendorCreditNoteAllocationDto> allocations
) {
    /** Lightweight factory — no allocations hydrated (list endpoint). */
    public static VendorCreditNoteDto from(VendorCreditNote c) {
        BigDecimal available = c.getTotalAmount().subtract(c.getAllocatedAmount());
        return new VendorCreditNoteDto(
            c.getId(), c.getUid(), c.getNumber(), c.getCompanyId(), c.getBranchId(),
            c.getSupplierId(), c.getVendorReturnId(), c.getCnDate(), c.getCurrencyCode(),
            c.getTotalAmount(), c.getAllocatedAmount(), available,
            c.getStatus(), c.getNotes(), null
        );
    }

    /** Full factory — includes allocation list (detail GET / apply response). */
    public static VendorCreditNoteDto from(VendorCreditNote c, List<VendorCreditNoteAllocationDto> allocations) {
        BigDecimal available = c.getTotalAmount().subtract(c.getAllocatedAmount());
        return new VendorCreditNoteDto(
            c.getId(), c.getUid(), c.getNumber(), c.getCompanyId(), c.getBranchId(),
            c.getSupplierId(), c.getVendorReturnId(), c.getCnDate(), c.getCurrencyCode(),
            c.getTotalAmount(), c.getAllocatedAmount(), available,
            c.getStatus(), c.getNotes(), allocations
        );
    }
}
