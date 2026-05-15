package com.orbix.engine.api;

import com.orbix.engine.modules.pos.domain.dto.CreateTillRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillDto;
import com.orbix.engine.modules.pos.domain.dto.UpdateTillRequestDto;
import com.orbix.engine.modules.pos.service.TillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Till master data (F5.1). Gated by {@code POS.MANAGE_TILL}. */
@RestController
@RequestMapping("/api/v1/tills")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('POS.MANAGE_TILL')")
public class TillController {

    private final TillService service;

    @GetMapping
    public List<TillDto> list(@RequestParam(required = false) Long branchId) {
        return service.list(branchId);
    }

    @GetMapping("/{id}")
    public TillDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<TillDto> create(@Valid @RequestBody CreateTillRequestDto request) {
        TillDto till = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tills/" + till.id())).body(till);
    }

    @PatchMapping("/{id}")
    public TillDto update(@PathVariable Long id, @Valid @RequestBody UpdateTillRequestDto request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public TillDto deactivate(@PathVariable Long id) {
        return service.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    public TillDto activate(@PathVariable Long id) {
        return service.activate(id);
    }
}
