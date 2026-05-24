package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record LpoOrderDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long supplierId,
    LocalDate orderDate,
    LocalDate expectedDeliveryDate,
    String currencyCode,
    BigDecimal subtotalAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    LpoOrderStatus status,
    Long approvedBy,
    Instant approvedAt,
    String notes,
    List<LpoOrderLineDto> lines
) {
    public static LpoOrderDto from(LpoOrder order, List<LpoOrderLine> lines) {
        return new LpoOrderDto(
            order.getId(),
            order.getUid(),
            order.getNumber(),
            order.getCompanyId(),
            order.getBranchId(),
            order.getSupplierId(),
            order.getOrderDate(),
            order.getExpectedDeliveryDate(),
            order.getCurrencyCode(),
            order.getSubtotalAmount(),
            order.getTaxAmount(),
            order.getTotalAmount(),
            order.getStatus(),
            order.getApprovedBy(),
            order.getApprovedAt(),
            order.getNotes(),
            lines.stream().map(LpoOrderLineDto::from).toList()
        );
    }
}
