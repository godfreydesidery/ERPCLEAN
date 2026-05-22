package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.party.domain.dto.CreateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.dto.SalesAgentResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSalesAgentRequestDto;
import com.orbix.engine.modules.party.service.SalesAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Sales-agent management (F1.7). Gated by {@code PARTY.MANAGE_AGENTS}. */
@RestController
@RequestMapping("/api/v1/sales-agents")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_AGENTS')")
@Validated
public class SalesAgentController {

    private final SalesAgentService service;

    @GetMapping
    public List<SalesAgentResponseDto> listSalesAgents() {
        return service.listSalesAgents();
    }

    @GetMapping("/uid/{partyUid}")
    public SalesAgentResponseDto getSalesAgent(@PathVariable @ValidUlid String partyUid) {
        return service.getSalesAgentByPartyUid(partyUid);
    }

    @PostMapping
    public ResponseEntity<SalesAgentResponseDto> createSalesAgent(
            @Valid @RequestBody CreateSalesAgentRequestDto request) {
        SalesAgentResponseDto agent = service.createSalesAgent(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-agents/uid/" + agent.party().uid()))
            .body(agent);
    }

    @PatchMapping("/uid/{partyUid}")
    public SalesAgentResponseDto updateSalesAgent(@PathVariable @ValidUlid String partyUid,
                                                  @Valid @RequestBody UpdateSalesAgentRequestDto request) {
        return service.updateSalesAgentByPartyUid(partyUid, request);
    }

    @PostMapping("/uid/{partyUid}/deactivate")
    public ResponseEntity<Void> deactivateSalesAgent(@PathVariable @ValidUlid String partyUid) {
        service.deactivateSalesAgentByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{partyUid}/activate")
    public ResponseEntity<Void> activateSalesAgent(@PathVariable @ValidUlid String partyUid) {
        service.activateSalesAgentByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }
}
