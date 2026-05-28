package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.repository.SupplierRepository;
import com.orbix.engine.modules.procurement.domain.dto.CreateSupplierInvoiceRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierInvoiceDto;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoiceGrn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceGrnRepository;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
import lombok.RequiredArgsConstructor;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class SupplierInvoiceServiceImpl implements SupplierInvoiceService {

    private static final String AGG = "SupplierInvoice";
    private static final String F_ID = "supplierInvoiceId";
    private static final String F_NUMBER = "number";

    private final SupplierInvoiceRepository invoices;
    private final SupplierInvoiceGrnRepository allocations;
    private final GrnRepository grns;
    private final SupplierRepository suppliers;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final SettingsService settings;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public SupplierInvoiceDto createDraft(CreateSupplierInvoiceRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        String supplierInvoiceNo = request.supplierInvoiceNo().trim();

        if (invoices.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Invoice number already exists for this branch: " + number);
        }
        if (invoices.existsBySupplierIdAndSupplierInvoiceNo(request.supplierId(), supplierInvoiceNo)) {
            throw new IllegalArgumentException(
                "Supplier invoice number already on file for this supplier: " + supplierInvoiceNo);
        }

        Supplier supplier = requireSupplier(request.supplierId());
        validateAllocations(request, companyId, null);
        BigDecimal total = request.subtotalAmount().add(request.taxAmount());
        BigDecimal allocated = sumAllocations(request);
        requireWithinTolerance(total, allocated);

        LocalDate dueDate = request.dueDate() != null
            ? request.dueDate()
            : request.invoiceDate().plusDays(supplier.getPaymentTermsDays());

        SupplierInvoice invoice = invoices.save(new SupplierInvoice(
            number, supplierInvoiceNo, companyId, request.branchId(), request.supplierId(),
            request.invoiceDate(), dueDate, request.currencyCode(),
            request.subtotalAmount(), request.taxAmount(), request.notes(), actorId
        ));
        List<SupplierInvoiceGrn> savedAllocs = new ArrayList<>(request.allocations().size());
        for (CreateSupplierInvoiceRequestDto.Allocation alloc : request.allocations()) {
            savedAllocs.add(allocations.save(new SupplierInvoiceGrn(
                invoice.getId(), alloc.grnId(), alloc.amount())));
        }

        events.publish("SupplierInvoiceCreated.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber(),
                "supplierId", invoice.getSupplierId(),
                "totalAmount", invoice.getTotalAmount(),
                "grnCount", savedAllocs.size()));
        return SupplierInvoiceDto.from(invoice, savedAllocs);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public SupplierInvoiceDto post(String uid) {
        SupplierInvoice invoice = requireInvoiceByUid(uid);
        List<SupplierInvoiceGrn> rows = allocations.findBySupplierInvoiceId(invoice.getId());
        BigDecimal allocated = rows.stream()
            .map(SupplierInvoiceGrn::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        requireWithinTolerance(invoice.getTotalAmount(), allocated);

        invoice.post(context.userId());
        events.publish("SupplierInvoiceMatched.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber(),
                "supplierId", invoice.getSupplierId(),
                "totalAmount", invoice.getTotalAmount(),
                "dueDate", invoice.getDueDate().toString(),
                "grnCount", rows.size()));
        return SupplierInvoiceDto.from(invoice, rows);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public SupplierInvoiceDto cancel(String uid) {
        SupplierInvoice invoice = requireInvoiceByUid(uid);
        invoice.cancel(context.userId());
        events.publish("SupplierInvoiceCancelled.v1", AGG, String.valueOf(invoice.getId()),
            Map.of(F_ID, invoice.getId(), F_NUMBER, invoice.getNumber()));
        return SupplierInvoiceDto.from(invoice, allocations.findBySupplierInvoiceId(invoice.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<SupplierInvoiceDto> list(Long branchId, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        Page<SupplierInvoice> page = scope == null
            ? invoices.findByCompanyIdOrderByIdDesc(companyId, pageable)
            : invoices.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope, pageable);
        return PageDto.of(page, i -> SupplierInvoiceDto.from(i, allocations.findBySupplierInvoiceId(i.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierInvoiceDto get(String uid) {
        SupplierInvoice invoice = requireInvoiceByUid(uid);
        return SupplierInvoiceDto.from(invoice, allocations.findBySupplierInvoiceId(invoice.getId()));
    }

    private void validateAllocations(CreateSupplierInvoiceRequestDto request, Long companyId,
                                     Long invoiceIdBeingEdited) {
        for (CreateSupplierInvoiceRequestDto.Allocation alloc : request.allocations()) {
            Grn grn = grns.findById(alloc.grnId())
                .orElseThrow(() -> new NoSuchElementException("GRN not found: " + alloc.grnId()));
            if (!Objects.equals(grn.getCompanyId(), companyId)) {
                throw new NoSuchElementException("GRN not found: " + alloc.grnId());
            }
            if (grn.getStatus() != GrnStatus.POSTED) {
                throw new IllegalArgumentException(
                    "GRN " + grn.getNumber() + " must be POSTED (was " + grn.getStatus() + ")");
            }
            if (!Objects.equals(grn.getSupplierId(), request.supplierId())) {
                throw new IllegalArgumentException(
                    "GRN " + grn.getNumber() + " is for a different supplier");
            }
            BigDecimal alreadyAllocated = allocations.sumAllocatedToGrn(grn.getId(), invoiceIdBeingEdited);
            BigDecimal projected = alreadyAllocated.add(alloc.amount());
            if (projected.compareTo(grn.getTotalAmount()) > 0) {
                throw new IllegalArgumentException(
                    "Over-allocation on GRN " + grn.getNumber() + ": already allocated "
                        + alreadyAllocated + ", attempting " + alloc.amount()
                        + ", GRN total is " + grn.getTotalAmount());
            }
        }
    }

    private BigDecimal sumAllocations(CreateSupplierInvoiceRequestDto request) {
        return request.allocations().stream()
            .map(CreateSupplierInvoiceRequestDto.Allocation::amount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void requireWithinTolerance(BigDecimal invoiceTotal, BigDecimal allocated) {
        if (invoiceTotal.signum() == 0) {
            if (allocated.signum() != 0) {
                throw new IllegalArgumentException("Invoice total is 0 but allocations sum to " + allocated);
            }
            return;
        }
        BigDecimal diff = invoiceTotal.subtract(allocated).abs();
        BigDecimal allowed = invoiceTotal.multiply(settings.getDecimal(SettingKey.PROCUREMENT_INVOICE_MATCH_TOLERANCE)).abs()
            .setScale(4, RoundingMode.HALF_UP);
        if (diff.compareTo(allowed) > 0) {
            throw new IllegalArgumentException(
                "Invoice total " + invoiceTotal + " differs from allocated "
                    + allocated + " by " + diff + ", outside tolerance " + allowed);
        }
    }

    private SupplierInvoice requireInvoiceByUid(String uid) {
        SupplierInvoice invoice = invoices.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Supplier invoice not found: " + uid));
        if (!Objects.equals(invoice.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Supplier invoice not found: " + uid);
        }
        branchScope.requireAccess(invoice.getBranchId());
        return invoice;
    }

    @Override
    @Transactional
    public void applyWriteOff(Long invoiceId, BigDecimal amount) {
        SupplierInvoice invoice = invoices.findById(invoiceId)
            .orElseThrow(() -> new NoSuchElementException("Supplier invoice not found: " + invoiceId));
        Long actorId = context.userId();
        invoice.applyPayment(amount, actorId);
    }

    private Supplier requireSupplier(Long supplierId) {
        // Company scoping is enforced via the GRN allocation validation: every
        // referenced GRN is loaded by company, and its supplier_id must match
        // request.supplierId(), so this supplier is implicitly in-company once
        // any allocation passes. @NotEmpty on the allocations list guarantees ≥1.
        return suppliers.findById(supplierId)
            .orElseThrow(() -> new NoSuchElementException("Supplier not found: " + supplierId));
    }
}
