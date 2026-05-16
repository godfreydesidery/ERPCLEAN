package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.day.service.EodBlockerDto;
import com.orbix.engine.modules.day.service.EodGuard;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Procurement EOD gate (F7.5 / TC-DAY-008). Any DRAFT GRN for the branch
 * blocks close — operator must either post (commits to stock ledger) or
 * cancel. Supplier invoices + payments + LPOs are decoupled from the
 * business-day cycle (DRAFTs can legitimately live across days while
 * awaiting supplier paperwork) so they don't gate the close in MVP.
 */
@Component
@RequiredArgsConstructor
public class ProcurementEodGuard implements EodGuard {

    private final GrnRepository grns;

    @Override
    @Transactional(readOnly = true)
    public List<EodBlockerDto> check(Long branchId, LocalDate businessDate) {
        return grns.findByBranchIdAndStatus(branchId, GrnStatus.DRAFT).stream()
            .map(this::toBlocker)
            .toList();
    }

    @Override
    public String moduleName() {
        return "procurement";
    }

    private EodBlockerDto toBlocker(Grn grn) {
        return new EodBlockerDto(moduleName(), "DRAFT_GRN", "Grn", grn.getId(),
            "GRN " + grn.getNumber() + " is DRAFT — post or cancel before day-end");
    }
}
