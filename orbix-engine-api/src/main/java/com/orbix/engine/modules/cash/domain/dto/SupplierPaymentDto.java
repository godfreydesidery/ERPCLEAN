package com.orbix.engine.modules.cash.domain.dto;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import com.orbix.engine.modules.cash.domain.entity.SupplierPaymentAllocation;
import com.orbix.engine.modules.cash.domain.enums.PaymentMethod;
import com.orbix.engine.modules.cash.domain.enums.SupplierPaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SupplierPaymentDto(
    Long id,
    String number,
    Long companyId,
    Long branchId,
    Long supplierId,
    LocalDate paymentDate,
    PaymentMethod method,
    String reference,
    String currencyCode,
    BigDecimal totalAmount,
    BigDecimal allocatedAmount,
    SupplierPaymentStatus status,
    Instant postedAt,
    Long postedBy,
    String notes,
    List<SupplierPaymentAllocationDto> allocations
) {
    public static SupplierPaymentDto from(SupplierPayment p, List<SupplierPaymentAllocation> allocs) {
        return new SupplierPaymentDto(
            p.getId(),
            p.getNumber(),
            p.getCompanyId(),
            p.getBranchId(),
            p.getSupplierId(),
            p.getPaymentDate(),
            p.getMethod(),
            p.getReference(),
            p.getCurrencyCode(),
            p.getTotalAmount(),
            p.getAllocatedAmount(),
            p.getStatus(),
            p.getPostedAt(),
            p.getPostedBy(),
            p.getNotes(),
            allocs.stream().map(SupplierPaymentAllocationDto::from).toList()
        );
    }
}
