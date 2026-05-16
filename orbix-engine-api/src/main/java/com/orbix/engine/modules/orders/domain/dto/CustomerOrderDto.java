package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.entity.CustomerOrder;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrderLine;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrderPayment;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CustomerOrderDto(
    Long id,
    String number,
    Long companyId,
    Long branchId,
    Long sectionId,
    Long customerId,
    CustomerOrderType type,
    CustomerOrderStatus status,
    String currencyCode,
    Instant reservedUntil,
    BigDecimal depositRequiredAmount,
    BigDecimal depositPaidAmount,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal balanceDue,
    BigDecimal refundedAmount,
    BigDecimal forfeitedAmount,
    Instant reservedAt,
    Instant collectedAt,
    Instant cancelledAt,
    String cancelReason,
    Instant expiredAt,
    String notes,
    List<CustomerOrderLineDto> lines,
    List<CustomerOrderPaymentDto> payments
) {
    public static CustomerOrderDto from(CustomerOrder o, List<CustomerOrderLine> lines,
                                        List<CustomerOrderPayment> payments) {
        return new CustomerOrderDto(
            o.getId(),
            o.getNumber(),
            o.getCompanyId(),
            o.getBranchId(),
            o.getSectionId(),
            o.getCustomerId(),
            o.getType(),
            o.getStatus(),
            o.getCurrencyCode(),
            o.getReservedUntil(),
            o.getDepositRequiredAmount(),
            o.getDepositPaidAmount(),
            o.getTotalAmount(),
            o.getPaidAmount(),
            o.getBalanceDue(),
            o.getRefundedAmount(),
            o.getForfeitedAmount(),
            o.getReservedAt(),
            o.getCollectedAt(),
            o.getCancelledAt(),
            o.getCancelReason(),
            o.getExpiredAt(),
            o.getNotes(),
            lines.stream().map(CustomerOrderLineDto::from).toList(),
            payments.stream().map(CustomerOrderPaymentDto::from).toList()
        );
    }
}
