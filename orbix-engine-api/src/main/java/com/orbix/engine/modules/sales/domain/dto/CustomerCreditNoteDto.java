package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

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
    CreditNoteStatus status,
    String notes
) {
    public static CustomerCreditNoteDto from(CustomerCreditNote c) {
        return new CustomerCreditNoteDto(c.getId(), c.getUid(), c.getNumber(), c.getCompanyId(), c.getBranchId(),
            c.getCustomerId(), c.getCustomerReturnId(), c.getCnDate(), c.getCurrencyCode(),
            c.getTotalAmount(), c.getAllocatedAmount(), c.getStatus(), c.getNotes());
    }
}
