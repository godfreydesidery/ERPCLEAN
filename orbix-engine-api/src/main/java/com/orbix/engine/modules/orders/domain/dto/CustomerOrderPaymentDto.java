package com.orbix.engine.modules.orders.domain.dto;

import com.orbix.engine.modules.orders.domain.entity.CustomerOrderPayment;
import com.orbix.engine.modules.orders.domain.enums.OrderPaymentDirection;
import com.orbix.engine.modules.orders.domain.enums.OrderPaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerOrderPaymentDto(
    Long id,
    Long customerOrderId,
    BigDecimal amount,
    OrderPaymentMethod method,
    OrderPaymentDirection direction,
    String reference,
    String notes,
    Instant occurredAt,
    Long byUserId,
    Long refCashEntryId,
    Long refGiftcardTxnId
) {
    public static CustomerOrderPaymentDto from(CustomerOrderPayment p) {
        return new CustomerOrderPaymentDto(
            p.getId(),
            p.getCustomerOrderId(),
            p.getAmount(),
            p.getMethod(),
            p.getDirection(),
            p.getReference(),
            p.getNotes(),
            p.getOccurredAt(),
            p.getByUserId(),
            p.getRefCashEntryId(),
            p.getRefGiftcardTxnId()
        );
    }
}
