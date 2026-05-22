package com.orbix.engine.api;

import com.orbix.engine.modules.stock.domain.dto.PostAdjustmentRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.service.AdjustmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Manager-initiated stock adjustments (F2.5). Gated by {@code STOCK.ADJUST}. */
@RestController
@RequestMapping("/api/v1/adjustments")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STOCK.ADJUST')")
public class AdjustmentController {

    private final AdjustmentService service;

    @PostMapping
    public StockMoveDto postAdjustment(@Valid @RequestBody PostAdjustmentRequestDto request) {
        return service.postAdjustment(request);
    }
}
