package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import com.orbix.engine.modules.cash.repository.SupplierPaymentRepository;
import com.orbix.engine.modules.common.domain.dto.PartyStatementDto;
import com.orbix.engine.modules.common.domain.dto.StatementEntryDto;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.CustomerCreditNoteRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.repository.SalesReceiptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatementReportServiceImpl implements StatementReportService {

    private final SalesInvoiceRepository salesInvoices;
    private final SalesReceiptRepository salesReceipts;
    private final CustomerCreditNoteRepository creditNotes;
    private final SupplierInvoiceRepository supplierInvoices;
    private final SupplierPaymentRepository supplierPayments;

    // ---------------------------------------------------------------------
    // Customer (AR)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PartyStatementDto customerStatement(Long customerId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();

        BigDecimal opening = nz(salesInvoices.sumOutstandingBefore(customerId, start))
            .subtract(nz(salesReceipts.sumReceiptsBefore(customerId, start)))
            .subtract(nz(creditNotes.sumCreditNotesBefore(customerId, start)));

        List<StatementEntryDto> entries = new ArrayList<>();
        for (SalesInvoice inv : salesInvoices.findForStatement(customerId, start, end)) {
            boolean voided = inv.getStatus() == SalesInvoiceStatus.VOIDED;
            entries.add(new StatementEntryDto(
                inv.getInvoiceDate(),
                "INVOICE",
                inv.getId(),
                inv.getNumber(),
                inv.getReference(),
                voided ? BigDecimal.ZERO : inv.getTotalAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                voided));
        }
        for (SalesReceipt r : salesReceipts.findForStatement(customerId, start, end)) {
            entries.add(new StatementEntryDto(
                r.getReceiptDate(),
                "RECEIPT",
                r.getId(),
                r.getNumber(),
                r.getReference(),
                BigDecimal.ZERO,
                r.getTotalAmount(),
                BigDecimal.ZERO,
                false));
        }
        for (CustomerCreditNote cn : creditNotes.findForStatement(customerId, start, end)) {
            entries.add(new StatementEntryDto(
                cn.getCnDate(),
                "CREDIT_NOTE",
                cn.getId(),
                cn.getNumber(),
                null,
                BigDecimal.ZERO,
                cn.getTotalAmount(),
                BigDecimal.ZERO,
                false));
        }
        return finalise(entries, opening, customerId, "CUSTOMER", start, end);
    }

    // ---------------------------------------------------------------------
    // Supplier (AP)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public PartyStatementDto supplierStatement(Long supplierId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();

        BigDecimal opening = nz(supplierInvoices.sumOutstandingBefore(supplierId, start))
            .subtract(nz(supplierPayments.sumPaymentsBefore(supplierId, start)));

        List<StatementEntryDto> entries = new ArrayList<>();
        for (SupplierInvoice inv : supplierInvoices.findForStatement(supplierId, start, end)) {
            entries.add(new StatementEntryDto(
                inv.getInvoiceDate(),
                "INVOICE",
                inv.getId(),
                inv.getNumber(),
                inv.getSupplierInvoiceNo(),
                inv.getTotalAmount(),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false));
        }
        for (SupplierPayment p : supplierPayments.findForStatement(supplierId, start, end)) {
            entries.add(new StatementEntryDto(
                p.getPaymentDate(),
                "PAYMENT",
                p.getId(),
                p.getNumber(),
                p.getReference(),
                BigDecimal.ZERO,
                p.getTotalAmount(),
                BigDecimal.ZERO,
                false));
        }
        return finalise(entries, opening, supplierId, "SUPPLIER", start, end);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Merge-sort the chronological entries, compute the running balance, and
     * roll up period debits + credits + closing.
     */
    private PartyStatementDto finalise(List<StatementEntryDto> raw, BigDecimal opening,
                                       Long partyId, String partyType,
                                       LocalDate from, LocalDate to) {
        raw.sort(Comparator.comparing(StatementEntryDto::date)
            .thenComparing(StatementEntryDto::kind));

        BigDecimal running = opening;
        BigDecimal totalDebits = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        List<StatementEntryDto> withRunning = new ArrayList<>(raw.size());
        for (StatementEntryDto e : raw) {
            running = running.add(e.debit()).subtract(e.credit());
            totalDebits = totalDebits.add(e.debit());
            totalCredits = totalCredits.add(e.credit());
            withRunning.add(new StatementEntryDto(
                e.date(), e.kind(), e.refId(), e.number(), e.reference(),
                e.debit(), e.credit(), running, e.voided()));
        }
        return new PartyStatementDto(partyId, partyType, from, to,
            opening, totalDebits, totalCredits, running, withRunning);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
