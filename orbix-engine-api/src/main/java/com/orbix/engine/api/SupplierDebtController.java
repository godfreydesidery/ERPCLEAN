package com.orbix.engine.api;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.validation.ValidUlid;
import com.orbix.engine.modules.procurement.domain.dto.SupplierAgingDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierDunningQueueRowDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierStatementDto;
import com.orbix.engine.modules.procurement.service.SupplierDebtReadModelService;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Slice G.1 — supplier-AP debt surface. AP aging buckets, dunning queue,
 * and supplier drill-down (statement).
 *
 * <p>Reuses the DEBT.* permission band (130-133) — no new permissions needed.
 * The class-level {@link PreAuthorize} gates everything with {@code DEBT.READ}.
 */
@RestController
@RequestMapping("/api/v1/debt")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasAuthority('DEBT.READ')")
public class SupplierDebtController {

    private final SupplierDebtReadModelService readModel;

    // ---------------------------------------------------------------------
    // AP Aging + dunning
    // ---------------------------------------------------------------------

    @GetMapping("/supplier-aging")
    public SupplierAgingDto aging(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return readModel.aging(branchId, asOf);
    }

    @GetMapping("/supplier-dunning")
    public PageDto<SupplierDunningQueueRowDto> dunning(
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) AgingBucket bucket,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        Page<SupplierDunningQueueRowDto> rows = readModel.dunning(branchId, bucket, PageRequest.of(page, size));
        return PageDto.of(rows, r -> r);
    }

    // ---------------------------------------------------------------------
    // Supplier statement / drill-down
    // ---------------------------------------------------------------------

    @GetMapping("/supplier/uid/{uid}")
    public SupplierStatementDto supplierStatement(@PathVariable @ValidUlid String uid) {
        return readModel.supplierStatement(uid);
    }
}
