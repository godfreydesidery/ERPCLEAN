package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.party.domain.dto.CreateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.dto.SalesAgentResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSalesAgentRequestDto;

import java.util.List;

/** Sales-agent-role management (F1.7). Reuses an existing party by TIN where possible. */
public interface SalesAgentService {

    List<SalesAgentResponseDto> listSalesAgents();

    SalesAgentResponseDto getSalesAgentByPartyUid(String partyUid);

    SalesAgentResponseDto createSalesAgent(CreateSalesAgentRequestDto request);

    SalesAgentResponseDto updateSalesAgentByPartyUid(String partyUid, UpdateSalesAgentRequestDto request);

    /** Archives the underlying party (affects every role on it). */
    void archiveSalesAgentByPartyUid(String partyUid);

    /**
     * Restores the underlying party to ACTIVE. Affects every other role on the
     * party (an archived customer-and-agent comes back as both).
     */
    void activateSalesAgentByPartyUid(String partyUid);
}
