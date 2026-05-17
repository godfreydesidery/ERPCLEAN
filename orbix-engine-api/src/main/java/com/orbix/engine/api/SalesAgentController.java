package com.orbix.engine.api;

import com.orbix.engine.modules.party.domain.dto.CreateSalesAgentRequestDto;
import com.orbix.engine.modules.party.domain.dto.SalesAgentResponseDto;
import com.orbix.engine.modules.party.domain.dto.UpdateSalesAgentRequestDto;
import com.orbix.engine.modules.party.service.SalesAgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Sales-agent management (F1.7). Gated by {@code PARTY.MANAGE_AGENTS}. */
@RestController
@RequestMapping("/api/v1/sales-agents")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PARTY.MANAGE_AGENTS')")
public class SalesAgentController {

    private final SalesAgentService service;

    @GetMapping
    public List<SalesAgentResponseDto> listSalesAgents() {
        return service.listSalesAgents();
    }

    @GetMapping("/{partyId}")
    public SalesAgentResponseDto getSalesAgent(@PathVariable Long partyId) {
        return service.getSalesAgent(partyId);
    }

    @PostMapping
    public ResponseEntity<SalesAgentResponseDto> createSalesAgent(
            @Valid @RequestBody CreateSalesAgentRequestDto request) {
        SalesAgentResponseDto agent = service.createSalesAgent(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-agents/" + agent.partyId()))
            .body(agent);
    }

    @PatchMapping("/{partyId}")
    public SalesAgentResponseDto updateSalesAgent(@PathVariable Long partyId,
                                                  @Valid @RequestBody UpdateSalesAgentRequestDto request) {
        return service.updateSalesAgent(partyId, request);
    }

    @PostMapping("/{partyId}/deactivate")
    public ResponseEntity<Void> deactivateSalesAgent(@PathVariable Long partyId) {
        service.deactivateSalesAgent(partyId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{partyId}/activate")
    public ResponseEntity<Void> activateSalesAgent(@PathVariable Long partyId) {
        service.activateSalesAgent(partyId);
        return ResponseEntity.noContent().build();
    }
}
