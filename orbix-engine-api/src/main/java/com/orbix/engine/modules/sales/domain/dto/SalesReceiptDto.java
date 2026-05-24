package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.ReceiptAllocation;
import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import com.orbix.engine.modules.sales.domain.enums.ReceiptMethod;
import com.orbix.engine.modules.sales.domain.enums.SalesReceiptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SalesReceiptDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long customerId,
    LocalDate receiptDate,
    ReceiptMethod method,
    String reference,
    String currencyCode,
    BigDecimal totalAmount,
    BigDecimal allocatedAmount,
    BigDecimal unallocatedAmount,
    SalesReceiptStatus status,
    Instant postedAt,
    Long postedBy,
    String notes,
    List<ReceiptAllocationDto> allocations
) {
    public static SalesReceiptDto from(SalesReceipt r, List<ReceiptAllocation> allocs) {
        return new SalesReceiptDto(
            r.getId(), r.getUid(), r.getNumber(), r.getCompanyId(), r.getBranchId(), r.getCustomerId(),
            r.getReceiptDate(), r.getMethod(), r.getReference(), r.getCurrencyCode(),
            r.getTotalAmount(), r.getAllocatedAmount(), r.getUnallocatedAmount(),
            r.getStatus(), r.getPostedAt(), r.getPostedBy(), r.getNotes(),
            allocs.stream().map(ReceiptAllocationDto::from).toList()
        );
    }
}
