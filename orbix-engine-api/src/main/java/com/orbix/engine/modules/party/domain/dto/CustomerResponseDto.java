package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;

import java.math.BigDecimal;

public record CustomerResponseDto(
    Long partyId,
    PartyResponseDto party,
    BigDecimal creditLimitAmount,
    int creditTermsDays,
    Long priceListId,
    Long defaultSalesAgentId,
    Long defaultBranchId,
    boolean walkIn,
    boolean taxExempt
) {
    public static CustomerResponseDto from(Customer customer, Party party) {
        return new CustomerResponseDto(
            customer.getPartyId(),
            PartyResponseDto.from(party),
            customer.getCreditLimitAmount(),
            customer.getCreditTermsDays(),
            customer.getPriceListId(),
            customer.getDefaultSalesAgentId(),
            customer.getDefaultBranchId(),
            customer.isWalkIn(),
            customer.isTaxExempt()
        );
    }
}
