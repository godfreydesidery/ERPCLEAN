package com.orbix.engine.modules.pos.domain.dto;

import com.orbix.engine.modules.pos.domain.entity.PosPayment;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;

import java.math.BigDecimal;

public record PosPaymentDto(
    Long id,
    PosPaymentMethod method,
    BigDecimal amount,
    String tenderCurrency,
    BigDecimal tenderAmount,
    BigDecimal fxRateSnapshot,
    String reference,
    String terminalId,
    String last4
) {
    public static PosPaymentDto from(PosPayment p) {
        return new PosPaymentDto(p.getId(), p.getMethod(), p.getAmount(),
            p.getTenderCurrency(), p.getTenderAmount(), p.getFxRateSnapshot(),
            p.getReference(), p.getTerminalId(), p.getLast4());
    }
}
