package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import com.orbix.engine.modules.cash.repository.SupplierPaymentRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.party.repository.SupplierRepository;
import com.orbix.engine.modules.procurement.domain.dto.SupplierAgingDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierDunningQueueRowDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierStatementDto;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Slice G.1 — AP debt read model. Aging buckets, dunning queue,
 * single-supplier statement.
 *
 * <p>Aging is computed in-memory in Java rather than as a JPQL aggregation
 * for DB-portability — same reasoning as {@code DebtReadModelServiceImpl} (AR side).
 * The single backing index is {@code ix_supplier_invoice_branch_due} (V72).
 */
@Service
@RequiredArgsConstructor
public class SupplierDebtReadModelServiceImpl implements SupplierDebtReadModelService {

    private static final String DEFAULT_CURRENCY = "TZS";
    private static final int DECIMAL_SCALE = 4;

    private final SupplierInvoiceRepository invoices;
    private final SupplierPaymentRepository payments;
    private final SupplierRepository suppliers;
    private final PartyRepository parties;
    private final RequestContext context;
    private final BranchScope branchScope;

    // ---------------------------------------------------------------------
    // Aging
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SupplierAgingDto aging(Long branchId, LocalDate asOf) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        LocalDate today = asOf != null ? asOf : LocalDate.now();

        List<SupplierInvoice> open = invoices.findAllOpenForAging(companyId, scope);
        Map<Long, SupplierAgingAccumulator> bySupplier = new LinkedHashMap<>();
        for (SupplierInvoice inv : open) {
            bySupplier.computeIfAbsent(inv.getSupplierId(), SupplierAgingAccumulator::new)
                      .add(inv, today);
        }

        Map<Long, Supplier> supplierById = suppliers.findAllById(bySupplier.keySet())
            .stream().collect(Collectors.toMap(Supplier::getPartyId, s -> s));
        Map<Long, Party> partyById = parties.findAllById(bySupplier.keySet())
            .stream().collect(Collectors.toMap(Party::getId, p -> p));

        List<SupplierAgingDto.SupplierRow> rows = new ArrayList<>(bySupplier.size());
        BigDecimal tCur = BigDecimal.ZERO;
        BigDecimal t130 = BigDecimal.ZERO;
        BigDecimal t3160 = BigDecimal.ZERO;
        BigDecimal t6190 = BigDecimal.ZERO;
        BigDecimal t90p = BigDecimal.ZERO;
        BigDecimal tTotal = BigDecimal.ZERO;
        long activeSuppliers = 0;

        for (SupplierAgingAccumulator acc : bySupplier.values()) {
            BigDecimal supplierOutstanding = acc.totalOutstanding();
            if (supplierOutstanding.signum() <= 0) {
                continue;
            }
            Party party = partyById.get(acc.supplierId);
            rows.add(new SupplierAgingDto.SupplierRow(
                acc.supplierId,
                party != null ? party.getUid() : null,
                party != null ? party.getName() : null,
                acc.current,
                acc.d1_30,
                acc.d31_60,
                acc.d61_90,
                acc.d90_plus,
                supplierOutstanding,
                acc.oldestDaysOverdue
            ));
            tCur = tCur.add(acc.current);
            t130 = t130.add(acc.d1_30);
            t3160 = t3160.add(acc.d31_60);
            t6190 = t6190.add(acc.d61_90);
            t90p = t90p.add(acc.d90_plus);
            tTotal = tTotal.add(supplierOutstanding);
            activeSuppliers++;
        }
        // Sort by oldest-overdue desc (chase the worst first); nulls (CURRENT only) last.
        rows.sort(Comparator.comparing(
            SupplierAgingDto.SupplierRow::oldestDaysOverdue,
            Comparator.nullsLast(Comparator.reverseOrder())));

        return new SupplierAgingDto(
            today,
            scope,
            DEFAULT_CURRENCY,
            new SupplierAgingDto.Totals(tCur, t130, t3160, t6190, t90p, tTotal, activeSuppliers),
            rows
        );
    }

    // ---------------------------------------------------------------------
    // Dunning queue (paged operator view)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<SupplierDunningQueueRowDto> dunning(Long branchId, AgingBucket bucketFilter, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        LocalDate today = LocalDate.now();

        List<SupplierInvoice> open = invoices.findAllOpenForAging(companyId, scope);
        Map<Long, SupplierAgingAccumulator> bySupplier = new LinkedHashMap<>();
        for (SupplierInvoice inv : open) {
            bySupplier.computeIfAbsent(inv.getSupplierId(), SupplierAgingAccumulator::new)
                      .add(inv, today);
        }
        if (bySupplier.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Map<Long, Party> partyById = parties.findAllById(bySupplier.keySet())
            .stream().collect(Collectors.toMap(Party::getId, p -> p));

        List<SupplierDunningQueueRowDto> all = new ArrayList<>(bySupplier.size());
        for (SupplierAgingAccumulator acc : bySupplier.values()) {
            BigDecimal outstanding = acc.totalOutstanding();
            AgingBucket worst = acc.worstBucket();
            if (outstanding.signum() <= 0 || !matchesDunningFilter(worst, bucketFilter)) {
                continue;
            }
            Party party = partyById.get(acc.supplierId);
            all.add(new SupplierDunningQueueRowDto(
                acc.supplierId,
                party != null ? party.getUid() : null,
                party != null ? party.getName() : null,
                outstanding,
                acc.oldestDaysOverdue,
                acc.oldestDueDate,
                worst,
                acc.overdueInvoiceCount
            ));
        }
        // Default sort: oldest-overdue desc (chase the worst first), then outstanding desc.
        all.sort(Comparator.comparing(
                (SupplierDunningQueueRowDto r) -> r.oldestDaysOverdue() == null ? Integer.MIN_VALUE : r.oldestDaysOverdue())
            .reversed()
            .thenComparing(SupplierDunningQueueRowDto::totalOutstanding, Comparator.reverseOrder()));

        int total = all.size();
        int from = (int) Math.min((long) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(all.subList(from, to), pageable, total);
    }

    // ---------------------------------------------------------------------
    // Supplier drill-down (statement)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SupplierStatementDto supplierStatement(String supplierUid) {
        return buildStatement(requireSupplierByUid(supplierUid));
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    /**
     * Returns {@code true} when the accumulator's worst bucket should appear
     * in the dunning queue.
     *
     * <ul>
     *   <li>Explicit {@code bucketFilter}: only rows matching that bucket pass.</li>
     *   <li>No filter ({@code null}): only suppliers with at least one overdue
     *       invoice pass — CURRENT-only suppliers are excluded per spec
     *       ("returns overdue suppliers").</li>
     * </ul>
     */
    private static boolean matchesDunningFilter(AgingBucket worst, AgingBucket bucketFilter) {
        if (bucketFilter != null) {
            return worst == bucketFilter;
        }
        // Unfiltered queue: exclude suppliers with no overdue invoices.
        return worst != AgingBucket.CURRENT;
    }

    private SupplierStatementDto buildStatement(SupplierWithParty swp) {
        LocalDate today = LocalDate.now();
        // Open invoices — cap at 100, dueDate asc.
        List<SupplierInvoice> open = invoices.findOpenForSupplier(
            swp.supplier.getPartyId(), PageRequest.of(0, 100));
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        long overdueCount = 0;
        List<SupplierStatementDto.OpenInvoiceRow> openRows = new ArrayList<>(open.size());
        String currencyCode = null;

        // Build aging buckets inline for the per-supplier aging row.
        SupplierAgingAccumulator acc = new SupplierAgingAccumulator(swp.supplier.getPartyId());

        for (SupplierInvoice inv : open) {
            BigDecimal outstanding = inv.getTotalAmount().subtract(inv.getPaidAmount());
            totalOutstanding = totalOutstanding.add(outstanding);
            Integer daysOverdue = null;
            if (inv.getDueDate() != null && inv.getDueDate().isBefore(today)) {
                daysOverdue = (int) ChronoUnit.DAYS.between(inv.getDueDate(), today);
                overdueCount++;
            }
            if (currencyCode == null) currencyCode = inv.getCurrencyCode();
            openRows.add(new SupplierStatementDto.OpenInvoiceRow(
                inv.getId(),
                inv.getUid(),
                inv.getNumber(),
                inv.getSupplierInvoiceNo(),
                inv.getInvoiceDate(),
                inv.getDueDate(),
                inv.getTotalAmount(),
                inv.getPaidAmount(),
                outstanding,
                daysOverdue,
                inv.getStatus()
            ));
            acc.add(inv, today);
        }

        SupplierAgingDto.SupplierRow agingRow = new SupplierAgingDto.SupplierRow(
            swp.supplier.getPartyId(),
            swp.party.getUid(),
            swp.party.getName(),
            acc.current,
            acc.d1_30,
            acc.d31_60,
            acc.d61_90,
            acc.d90_plus,
            acc.totalOutstanding(),
            acc.oldestDaysOverdue
        );

        // Recent payments — last 30 days, capped at 50, newest-first.
        LocalDate fromDate = today.minusDays(30);
        List<SupplierPayment> recent = payments.findRecentPostedForSupplier(
            swp.supplier.getPartyId(), fromDate, PageRequest.of(0, 50));
        List<SupplierStatementDto.RecentPaymentRow> paymentRows = new ArrayList<>(recent.size());
        for (SupplierPayment p : recent) {
            paymentRows.add(new SupplierStatementDto.RecentPaymentRow(
                p.getId(),
                p.getUid(),
                p.getNumber(),
                p.getPaymentDate(),
                p.getPostedAt(),
                p.getTotalAmount(),
                p.getCurrencyCode()
            ));
            if (currencyCode == null) currencyCode = p.getCurrencyCode();
        }

        return new SupplierStatementDto(
            swp.supplier.getPartyId(),
            swp.party.getUid(),
            swp.party.getName(),
            currencyCode != null ? currencyCode : DEFAULT_CURRENCY,
            totalOutstanding,
            openRows.size(),
            overdueCount,
            today,
            agingRow,
            openRows,
            paymentRows
        );
    }

    private SupplierWithParty requireSupplierByUid(String supplierUid) {
        Party party = parties.findByUid(supplierUid)
            .orElseThrow(() -> new NoSuchElementException("Supplier not found: " + supplierUid));
        if (!Objects.equals(party.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Supplier not found: " + supplierUid);
        }
        Supplier supplier = suppliers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException("Not a supplier: " + supplierUid));
        return new SupplierWithParty(supplier, party);
    }

    private record SupplierWithParty(Supplier supplier, Party party) {}

    /**
     * Mutable per-supplier accumulator used by {@link #aging} and {@link #dunning}.
     * Buckets per US-DEBT-003: CURRENT / 1-30 / 31-60 / 61-90 / 90+.
     */
    private static final class SupplierAgingAccumulator {
        final Long supplierId;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d1_30 = BigDecimal.ZERO;
        BigDecimal d31_60 = BigDecimal.ZERO;
        BigDecimal d61_90 = BigDecimal.ZERO;
        BigDecimal d90_plus = BigDecimal.ZERO;
        Integer oldestDaysOverdue;
        LocalDate oldestDueDate;
        long overdueInvoiceCount;
        Set<Long> invoiceIds = new HashSet<>();

        SupplierAgingAccumulator(Long supplierId) {
            this.supplierId = supplierId;
        }

        void add(SupplierInvoice inv, LocalDate today) {
            BigDecimal outstanding = inv.getTotalAmount().subtract(inv.getPaidAmount());
            if (outstanding.signum() <= 0) return;
            invoiceIds.add(inv.getId());
            LocalDate due = inv.getDueDate();
            if (due == null || !due.isBefore(today)) {
                current = current.add(outstanding);
                return;
            }
            int days = (int) ChronoUnit.DAYS.between(due, today);
            overdueInvoiceCount++;
            if (oldestDaysOverdue == null || days > oldestDaysOverdue) {
                oldestDaysOverdue = days;
                oldestDueDate = due;
            }
            if (days <= 30)        d1_30    = d1_30.add(outstanding);
            else if (days <= 60)   d31_60   = d31_60.add(outstanding);
            else if (days <= 90)   d61_90   = d61_90.add(outstanding);
            else                   d90_plus = d90_plus.add(outstanding);
        }

        BigDecimal totalOutstanding() {
            return current.add(d1_30).add(d31_60).add(d61_90).add(d90_plus);
        }

        AgingBucket worstBucket() {
            if (d90_plus.signum() > 0) return AgingBucket.D_90_PLUS;
            if (d61_90.signum() > 0)   return AgingBucket.D_61_90;
            if (d31_60.signum() > 0)   return AgingBucket.D_31_60;
            if (d1_30.signum() > 0)    return AgingBucket.D_1_30;
            return AgingBucket.CURRENT;
        }
    }
}
