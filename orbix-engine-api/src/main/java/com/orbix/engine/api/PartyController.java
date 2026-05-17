package com.orbix.engine.api;

import com.orbix.engine.modules.party.domain.dto.PartyResponseDto;
import com.orbix.engine.modules.party.service.PartyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Shared party lookups (F1.7). The TIN lookup backs the "this TIN already
 * exists — add the role to the existing party?" hint on the role-create forms;
 * the list endpoint backs the "pick existing party" picker on those same forms.
 */
@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService service;

    @GetMapping
    public List<PartyResponseDto> listParties() {
        return service.listParties();
    }

    @GetMapping("/by-tin")
    public PartyResponseDto findByTin(@RequestParam String tin) {
        return service.findByTin(tin)
            .orElseThrow(() -> new NoSuchElementException("No party with TIN: " + tin));
    }

    /**
     * Reserves the next free party code for the given prefix (e.g. {@code AGT}).
     * POST because each call increments the per-(company, prefix) counter.
     */
    @PostMapping("/codes/reserve")
    public Map<String, String> reserveCode(@RequestParam String prefix) {
        return Map.of("code", service.reservePartyCode(prefix));
    }
}
