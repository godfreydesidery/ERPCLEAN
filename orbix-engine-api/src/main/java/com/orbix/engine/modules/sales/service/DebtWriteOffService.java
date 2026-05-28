package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.sales.domain.dto.CreateDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.dto.DebtWriteOffDto;
import com.orbix.engine.modules.sales.domain.dto.RejectDebtWriteOffRequestDto;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import org.springframework.data.domain.Pageable;

/**
 * Slice G.2 — debt write-off (AR + AP, dual approval).
 * PENDING_APPROVAL → POSTED (approve) or REJECTED.
 * ADR-0004 sync-TX exemption #20 covers the same-TX call to
 * SalesInvoiceService/SupplierInvoiceService on post.
 */
public interface DebtWriteOffService {

    DebtWriteOffDto create(CreateDebtWriteOffRequestDto request);

    DebtWriteOffDto approve(String uid);

    DebtWriteOffDto reject(String uid, RejectDebtWriteOffRequestDto request);

    PageDto<DebtWriteOffDto> list(DebtWriteOffStatus status, DebtWriteOffTargetKind kind, Pageable pageable);

    DebtWriteOffDto get(String uid);
}
