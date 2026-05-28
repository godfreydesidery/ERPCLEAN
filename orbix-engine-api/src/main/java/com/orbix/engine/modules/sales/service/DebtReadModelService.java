package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.sales.domain.dto.AdjustCreditLimitRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerStatementDto;
import com.orbix.engine.modules.sales.domain.dto.DebtAgingDto;
import com.orbix.engine.modules.sales.domain.dto.DunningQueueRowDto;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Slice G — read model that powers the customer-AR debt surface:
 *
 * <ul>
 *   <li>{@link #aging(Long, LocalDate)} — per-customer 5-bucket aging rollup.</li>
 *   <li>{@link #dunning(Long, AgingBucket, Pageable)} — paged operator queue.</li>
 *   <li>{@link #customerStatement(String)} — single-customer drill-down.</li>
 *   <li>{@link #adjustCreditLimit(String, AdjustCreditLimitRequestDto)} —
 *       debt-surface entry point for credit-limit adjust (perm
 *       {@code DEBT.CREDIT_LIMIT.UPDATE}).</li>
 * </ul>
 *
 * <p>Lives in {@code modules.sales.service} per ADR-0005 — the data is
 * sales-domain receivables; the {@code DEBT.*} permission namespace is
 * the operator vocabulary at the URL layer.
 */
public interface DebtReadModelService {

    DebtAgingDto aging(Long branchId, LocalDate asOf);

    Page<DunningQueueRowDto> dunning(Long branchId, AgingBucket bucketFilter, Pageable pageable);

    CustomerStatementDto customerStatement(String customerUid);

    CustomerStatementDto adjustCreditLimit(String customerUid, AdjustCreditLimitRequestDto request);
}
