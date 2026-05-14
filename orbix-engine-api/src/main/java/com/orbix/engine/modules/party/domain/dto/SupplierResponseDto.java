package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.Supplier;

import java.math.BigDecimal;

public record SupplierResponseDto(
    Long partyId,
    PartyResponseDto party,
    int paymentTermsDays,
    BigDecimal creditLimitAmount,
    String defaultCurrencyCode,
    String bankName,
    String bankAccountNo,
    Integer leadTimeDays
) {
    public static SupplierResponseDto from(Supplier supplier, Party party) {
        return new SupplierResponseDto(
            supplier.getPartyId(),
            PartyResponseDto.from(party),
            supplier.getPaymentTermsDays(),
            supplier.getCreditLimitAmount(),
            supplier.getDefaultCurrencyCode(),
            supplier.getBankName(),
            supplier.getBankAccountNo(),
            supplier.getLeadTimeDays()
        );
    }
}
