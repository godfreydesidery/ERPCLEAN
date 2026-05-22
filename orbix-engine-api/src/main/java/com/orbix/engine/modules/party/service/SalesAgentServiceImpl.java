package com.orbix.engine.modules.party.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.party.domain.dto.CreateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.dto.SalesAgentResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.SalesAgent;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.party.repository.SalesAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesAgentServiceImpl implements SalesAgentService {

    private static final String NOT_AN_AGENT = "Not a sales agent: ";

    private final SalesAgentRepository salesAgents;
    private final PartyRepository parties;
    private final PartyService partyService;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<SalesAgentResponseDto> listSalesAgents() {
        List<SalesAgent> rows = salesAgents.findByCompanyId(context.companyId());
        Map<Long, Party> partyById = parties.findAllById(
                rows.stream().map(SalesAgent::getPartyId).toList())
            .stream().collect(Collectors.toMap(Party::getId, Function.identity()));
        return rows.stream()
            .map(a -> SalesAgentResponseDto.from(a, partyById.get(a.getPartyId())))
            .sorted(Comparator.comparing(SalesAgentResponseDto::agentCode))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesAgentResponseDto getSalesAgentByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        SalesAgent agent = salesAgents.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_AN_AGENT + partyUid));
        return SalesAgentResponseDto.from(agent, party);
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "SalesAgent")
    public SalesAgentResponseDto createSalesAgent(CreateSalesAgentRequestDto request) {
        Long companyId = context.companyId();
        String agentCode = request.agentCode().trim().toUpperCase();
        if (salesAgents.existsByCompanyIdAndAgentCode(companyId, agentCode)) {
            throw new IllegalArgumentException("Agent code already exists: " + agentCode);
        }
        Party party = resolveParty(request);
        if (salesAgents.existsById(party.getId())) {
            throw new IllegalArgumentException(
                "Party " + party.getCode() + " already has a sales-agent role");
        }
        SalesAgent agent = new SalesAgent(party.getId(), agentCode, request.branchId());
        agent.update(request.appUserId(), request.routeId(), request.commissionRate(),
            request.branchId());
        return SalesAgentResponseDto.from(salesAgents.save(agent), party);
    }

    private Party resolveParty(CreateSalesAgentRequestDto request) {
        if (request.partyId() != null) {
            return partyService.requireInCompany(request.partyId());
        }
        if (request.party() == null) {
            throw new IllegalArgumentException(
                "Supply either partyId, or party details, to create a sales agent");
        }
        String generatedCode = partyService.reservePartyCode("AGT");
        return partyService.resolveOrCreate(generatedCode, request.party(), context.userId());
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = "SalesAgent")
    public SalesAgentResponseDto updateSalesAgentByPartyUid(String partyUid, UpdateSalesAgentRequestDto request) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        SalesAgent agent = salesAgents.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_AN_AGENT + partyUid));
        partyService.applyDetails(party, request.party(), context.userId());
        agent.update(request.appUserId(), request.routeId(), request.commissionRate(),
            request.branchId());
        return SalesAgentResponseDto.from(agent, party);
    }

    @Override
    @Transactional
    @Auditable(action = "DEACTIVATE", entityType = "SalesAgent")
    public void deactivateSalesAgentByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        salesAgents.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_AN_AGENT + partyUid));
        partyService.deactivate(party.getId());
    }

    @Override
    @Transactional
    @Auditable(action = "ACTIVATE", entityType = "SalesAgent")
    public void activateSalesAgentByPartyUid(String partyUid) {
        Party party = partyService.requireInCompanyByUid(partyUid);
        salesAgents.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException(NOT_AN_AGENT + partyUid));
        partyService.activate(party.getId());
    }
}
