package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.sales.domain.dto.CreateCustomerReturnRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerCreditNoteDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerReturnDto;
import com.orbix.engine.modules.sales.domain.dto.IssueCreditNoteRequestDto;

import java.util.List;

/**
 * Customer returns + credit notes (F4.4). DRAFT → POSTED writes stock_move
 * rows (RETURN_IN if restock=true, DAMAGE otherwise). POSTED → CREDITED issues
 * a customer_credit_note. Credit-note allocation to open invoices is a
 * follow-on slice; for now the credit note is created in a POSTED state
 * carrying the full return amount.
 */
public interface CustomerReturnService {

    CustomerReturnDto createDraft(CreateCustomerReturnRequestDto request);

    CustomerReturnDto post(Long returnId);

    CustomerReturnDto cancel(Long returnId);

    CustomerCreditNoteDto issueCreditNote(Long returnId, IssueCreditNoteRequestDto request);

    List<CustomerReturnDto> list(Long branchId);

    CustomerReturnDto get(Long returnId);

    List<CustomerCreditNoteDto> listCreditNotes(Long branchId);
}
