package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PartyStatementDto;

import java.time.LocalDate;

/**
 * AR / AP statement reporting (F8.7 / US-RPT-007). Same envelope shape for
 * both customer and supplier — opening balance carried forward from before
 * the window, chronological debits + credits, closing balance.
 *
 * <p>AR composition: sales_invoice (debit) + sales_receipt (credit) +
 * customer_credit_note (credit). AP composition: supplier_invoice (debit) +
 * supplier_payment (credit). VOIDED sales invoices appear in the timeline
 * with zero debit/credit so the auditor can trace the original entry.
 */
public interface StatementReportService {

    PartyStatementDto customerStatement(Long customerId, LocalDate from, LocalDate to);

    PartyStatementDto supplierStatement(Long supplierId, LocalDate from, LocalDate to);
}
