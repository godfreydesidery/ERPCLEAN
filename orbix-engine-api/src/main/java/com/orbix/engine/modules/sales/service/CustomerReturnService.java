package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.sales.domain.dto.ApplyCreditNoteRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CreateCustomerReturnRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerCreditNoteDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerReturnDto;
import com.orbix.engine.modules.sales.domain.dto.IssueCreditNoteRequestDto;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Customer returns + credit notes (F4.4). DRAFT → POSTED writes stock_move
 * rows (RETURN_IN if restock=true, DAMAGE otherwise). POSTED → CREDITED issues
 * a customer_credit_note. Slice H adds credit-note allocation to open invoices.
 */
public interface CustomerReturnService {

    CustomerReturnDto createDraft(CreateCustomerReturnRequestDto request);

    CustomerReturnDto post(String uid);

    CustomerReturnDto cancel(String uid);

    CustomerCreditNoteDto issueCreditNote(String uid, IssueCreditNoteRequestDto request);

    /**
     * Slice H — apply a credit note to an open sales invoice in the same TX.
     * Returns the updated {@link CustomerCreditNoteDto} with refreshed
     * {@code allocatedAmount}, {@code availableAmount}, {@code status}, and the
     * full {@code allocations} list.
     */
    CustomerCreditNoteDto applyToInvoice(String creditNoteUid, ApplyCreditNoteRequestDto request);

    /**
     * Slice H — detail GET for a single credit note; hydrates the allocations list.
     */
    CustomerCreditNoteDto getCreditNote(String uid);

    PageDto<CustomerReturnDto> list(Long branchId, Pageable pageable);

    CustomerReturnDto get(String uid);

    List<CustomerCreditNoteDto> listCreditNotes(Long branchId);
}
