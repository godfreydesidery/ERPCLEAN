package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.dto.SalesAgentResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSalesAgentRequestDto;

import java.util.List;

/** Sales-agent-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface SalesAgentService {

    List<SalesAgentResponseDto> listSalesAgents();

    SalesAgentResponseDto getSalesAgent(Long partyId);

    SalesAgentResponseDto createSalesAgent(CreateSalesAgentRequestDto request);

    SalesAgentResponseDto updateSalesAgent(Long partyId, UpdateSalesAgentRequestDto request);

    void deactivateSalesAgent(Long partyId);
}
