package com.orbix.engine.api;

import com.orbix.engine.modules.stock.domain.dto.PostInternalConsumptionRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.service.InternalConsumptionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Internal-consumption draws (F2.5). Gated by {@code STOCK.INTERNAL_CONSUMPTION}. */
@RestController
@RequestMapping("/api/v1/internal-consumption")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STOCK.INTERNAL_CONSUMPTION')")
public class InternalConsumptionController {

    private final InternalConsumptionService service;

    @PostMapping
    public StockMoveDto post(@Valid @RequestBody PostInternalConsumptionRequestDto request) {
        return service.postInternalConsumption(request);
    }
}
