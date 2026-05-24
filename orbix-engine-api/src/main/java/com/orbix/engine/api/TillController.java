package com.orbix.engine.api;

import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.pos.domain.dto.CreateTillRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillDto;
import com.orbix.engine.modules.pos.domain.dto.UpdateTillRequestDto;
import com.orbix.engine.modules.pos.service.TillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** Till master data (F5.1). Gated by {@code POS.MANAGE_TILL}. */
@RestController
@RequestMapping("/api/v1/tills")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('POS.MANAGE_TILL')")
public class TillController {

    private final TillService service;

    @GetMapping
    public List<TillDto> list(@RequestParam(required = false) Long branchId) {
        return service.list(branchId);
    }

    @GetMapping("/uid/{uid}")
    public TillDto get(@PathVariable @ValidUlid String uid) {
        return service.get(uid);
    }

    @PostMapping
    public ResponseEntity<TillDto> create(@Valid @RequestBody CreateTillRequestDto request) {
        TillDto till = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/tills/uid/" + till.uid())).body(till);
    }

    @PatchMapping("/uid/{uid}")
    public TillDto update(@PathVariable @ValidUlid String uid, @Valid @RequestBody UpdateTillRequestDto request) {
        return service.update(uid, request);
    }

    @PostMapping("/uid/{uid}/deactivate")
    public TillDto deactivate(@PathVariable @ValidUlid String uid) {
        return service.deactivate(uid);
    }

    @PostMapping("/uid/{uid}/activate")
    public TillDto activate(@PathVariable @ValidUlid String uid) {
        return service.activate(uid);
    }
}
