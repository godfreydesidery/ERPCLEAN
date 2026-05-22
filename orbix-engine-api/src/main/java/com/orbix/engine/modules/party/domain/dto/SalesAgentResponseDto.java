package com.orbix.engine.modules.party.domain.dto;

import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.SalesAgent;

import java.math.BigDecimal;

public record SalesAgentResponseDto(
    Long partyId,
    PartyResponseDto party,
    Long appUserId,
    String agentCode,
    Long routeId,
    BigDecimal commissionRate,
    Long branchId
) {
    public static SalesAgentResponseDto from(SalesAgent agent, Party party) {
        return new SalesAgentResponseDto(
            agent.getPartyId(),
            PartyResponseDto.from(party),
            agent.getAppUserId(),
            agent.getAgentCode(),
            agent.getRouteId(),
            agent.getCommissionRate(),
            agent.getBranchId()
        );
    }
}
