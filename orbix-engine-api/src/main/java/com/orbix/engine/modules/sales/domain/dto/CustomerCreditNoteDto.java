package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CustomerCreditNoteDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long customerId,
    Long customerReturnId,
    LocalDate cnDate,
    String currencyCode,
    BigDecimal totalAmount,
    BigDecimal allocatedAmount,
    BigDecimal availableAmount,
    CreditNoteStatus status,
    String notes,
    List<CreditNoteAllocationDto> allocations
) {
    /** Lightweight factory — no allocations hydrated (list endpoint). */
    public static CustomerCreditNoteDto from(CustomerCreditNote c) {
        BigDecimal available = c.getTotalAmount().subtract(c.getAllocatedAmount());
        return new CustomerCreditNoteDto(c.getId(), c.getUid(), c.getNumber(), c.getCompanyId(),
            c.getBranchId(), c.getCustomerId(), c.getCustomerReturnId(), c.getCnDate(),
            c.getCurrencyCode(), c.getTotalAmount(), c.getAllocatedAmount(), available,
            c.getStatus(), c.getNotes(), null);
    }

    /** Full factory — includes allocation list (detail GET / apply response). */
    public static CustomerCreditNoteDto from(CustomerCreditNote c, List<CreditNoteAllocationDto> allocations) {
        BigDecimal available = c.getTotalAmount().subtract(c.getAllocatedAmount());
        return new CustomerCreditNoteDto(c.getId(), c.getUid(), c.getNumber(), c.getCompanyId(),
            c.getBranchId(), c.getCustomerId(), c.getCustomerReturnId(), c.getCnDate(),
            c.getCurrencyCode(), c.getTotalAmount(), c.getAllocatedAmount(), available,
            c.getStatus(), c.getNotes(), allocations);
    }
}
