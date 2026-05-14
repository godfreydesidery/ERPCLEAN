package com.orbix.engine.api;

import com.orbix.engine.modules.party.domain.dto.PartyResponseDto;
import com.orbix.engine.modules.party.service.PartyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

/**
 * Shared party lookups (F1.7). The TIN lookup backs the "this TIN already
 * exists — add the role to the existing party?" hint on the role-create forms.
 */
@RestController
@RequestMapping("/api/v1/parties")
@RequiredArgsConstructor
public class PartyController {

    private final PartyService service;

    @GetMapping("/by-tin")
    public PartyResponseDto findByTin(@RequestParam String tin) {
        return service.findByTin(tin)
            .orElseThrow(() -> new NoSuchElementException("No party with TIN: " + tin));
    }
}
