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

/**
 * Sales-agent management (F1.7). Per-action permissions:
 * {@code SALES_AGENT.CREATE}, {@code SALES_AGENT.UPDATE}, {@code SALES_AGENT.ARCHIVE}
 * (archive perm also gates re-activation, the inverse of archive).
 */
@RestController
@RequestMapping("/api/v1/sales-agents")
@RequiredArgsConstructor
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
    @PreAuthorize("hasAuthority('SALES_AGENT.CREATE')")
    public ResponseEntity<SalesAgentResponseDto> createSalesAgent(
            @Valid @RequestBody CreateSalesAgentRequestDto request) {
        SalesAgentResponseDto agent = service.createSalesAgent(request);
        return ResponseEntity.created(URI.create("/api/v1/sales-agents/uid/" + agent.party().uid()))
            .body(agent);
    }

    @PatchMapping("/uid/{partyUid}")
    @PreAuthorize("hasAuthority('SALES_AGENT.UPDATE')")
    public SalesAgentResponseDto updateSalesAgent(@PathVariable @ValidUlid String partyUid,
                                                  @Valid @RequestBody UpdateSalesAgentRequestDto request) {
        return service.updateSalesAgentByPartyUid(partyUid, request);
    }

    @PostMapping("/uid/{partyUid}/archive")
    @PreAuthorize("hasAuthority('SALES_AGENT.ARCHIVE')")
    public ResponseEntity<Void> archiveSalesAgent(@PathVariable @ValidUlid String partyUid) {
        service.archiveSalesAgentByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/uid/{partyUid}/activate")
    @PreAuthorize("hasAuthority('SALES_AGENT.ARCHIVE')")
    public ResponseEntity<Void> activateSalesAgent(@PathVariable @ValidUlid String partyUid) {
        service.activateSalesAgentByPartyUid(partyUid);
        return ResponseEntity.noContent().build();
    }
}
