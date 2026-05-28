package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.sales.domain.dto.AdjustCreditLimitRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerStatementDto;
import com.orbix.engine.modules.sales.domain.dto.DebtAgingDto;
import com.orbix.engine.modules.sales.domain.dto.DunningQueueRowDto;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.entity.SalesReceipt;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.repository.SalesReceiptRepository;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

/**
 * Slice G — debt read model. Aging buckets, dunning queue, single-customer
 * statement, and the debt-surface credit-limit adjust endpoint.
 *
 * <p>Aging is computed in-memory in Java rather than as a JPQL aggregation
 * with {@code function('datediff', ...)} for DB-portability — MariaDB and
 * Postgres disagree on the {@code datediff} signature, and the
 * pull-everything-open + group-in-Java path is cheap at the expected scale
 * (≤ ~5k open invoices per company per branch). The single backing index
 * is {@code ix_sales_invoice_branch_due} (V27).
 */
@Service
@RequiredArgsConstructor
public class DebtReadModelServiceImpl implements DebtReadModelService {

    private static final String DEFAULT_CURRENCY = "TZS";
    private static final int DECIMAL_SCALE = 4;

    private final SalesInvoiceRepository invoices;
    private final SalesReceiptRepository receipts;
    private final CustomerRepository customers;
    private final PartyRepository parties;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final EventPublisher events;

    // ---------------------------------------------------------------------
    // Aging
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public DebtAgingDto aging(Long branchId, LocalDate asOf) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        LocalDate today = asOf != null ? asOf : LocalDate.now();

        List<SalesInvoice> open = invoices.findAllOpenForAging(companyId, scope);
        Map<Long, CustomerAgingAccumulator> byCustomer = new LinkedHashMap<>();
        for (SalesInvoice inv : open) {
            CustomerAgingAccumulator acc = byCustomer.computeIfAbsent(
                inv.getCustomerId(), CustomerAgingAccumulator::new);
            acc.add(inv, today);
        }

        // Eager-load customer + party for the rows; bounded by the number of
        // distinct customers with open AR (cheap).
        Map<Long, Customer> customerById = customers.findAllById(byCustomer.keySet())
            .stream().collect(java.util.stream.Collectors.toMap(Customer::getPartyId, c -> c));
        Map<Long, Party> partyById = parties.findAllById(byCustomer.keySet())
            .stream().collect(java.util.stream.Collectors.toMap(Party::getId, p -> p));

        List<DebtAgingDto.CustomerRow> rows = new ArrayList<>(byCustomer.size());
        BigDecimal tCur = BigDecimal.ZERO;
        BigDecimal t130 = BigDecimal.ZERO;
        BigDecimal t3160 = BigDecimal.ZERO;
        BigDecimal t6190 = BigDecimal.ZERO;
        BigDecimal t90p = BigDecimal.ZERO;
        BigDecimal tTotal = BigDecimal.ZERO;
        long activeCustomers = 0;
        for (CustomerAgingAccumulator acc : byCustomer.values()) {
            BigDecimal customerOutstanding = acc.totalOutstanding();
            if (customerOutstanding.signum() <= 0) {
                continue;
            }
            Customer customer = customerById.get(acc.customerId);
            Party party = partyById.get(acc.customerId);
            BigDecimal creditLimit = customer != null && customer.getCreditLimitAmount() != null
                ? customer.getCreditLimitAmount() : BigDecimal.ZERO;
            BigDecimal utilisation = creditLimit.signum() > 0
                ? customerOutstanding.divide(creditLimit, DECIMAL_SCALE, RoundingMode.HALF_UP)
                : null;
            rows.add(new DebtAgingDto.CustomerRow(
                acc.customerId,
                party != null ? party.getUid() : null,
                party != null ? party.getName() : null,
                acc.current,
                acc.d1_30,
                acc.d31_60,
                acc.d61_90,
                acc.d90_plus,
                customerOutstanding,
                acc.oldestDaysOverdue,
                creditLimit,
                utilisation
            ));
            tCur = tCur.add(acc.current);
            t130 = t130.add(acc.d1_30);
            t3160 = t3160.add(acc.d31_60);
            t6190 = t6190.add(acc.d61_90);
            t90p = t90p.add(acc.d90_plus);
            tTotal = tTotal.add(customerOutstanding);
            activeCustomers++;
        }
        // Sort by oldest-overdue desc (chase the worst first); nulls (CURRENT only) last.
        rows.sort(Comparator.comparing(
            DebtAgingDto.CustomerRow::oldestDaysOverdue,
            Comparator.nullsLast(Comparator.reverseOrder())));

        return new DebtAgingDto(
            today,
            scope,
            DEFAULT_CURRENCY,
            new DebtAgingDto.Totals(tCur, t130, t3160, t6190, t90p, tTotal, activeCustomers),
            rows
        );
    }

    // ---------------------------------------------------------------------
    // Dunning queue (paged operator view)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<DunningQueueRowDto> dunning(Long branchId, AgingBucket bucketFilter, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        LocalDate today = LocalDate.now();

        List<SalesInvoice> open = invoices.findAllOpenForAging(companyId, scope);
        Map<Long, CustomerAgingAccumulator> byCustomer = new LinkedHashMap<>();
        for (SalesInvoice inv : open) {
            byCustomer.computeIfAbsent(inv.getCustomerId(), CustomerAgingAccumulator::new).add(inv, today);
        }
        if (byCustomer.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, 0);
        }
        Map<Long, Customer> customerById = customers.findAllById(byCustomer.keySet())
            .stream().collect(java.util.stream.Collectors.toMap(Customer::getPartyId, c -> c));
        Map<Long, Party> partyById = parties.findAllById(byCustomer.keySet())
            .stream().collect(java.util.stream.Collectors.toMap(Party::getId, p -> p));

        List<DunningQueueRowDto> all = new ArrayList<>(byCustomer.size());
        for (CustomerAgingAccumulator acc : byCustomer.values()) {
            BigDecimal outstanding = acc.totalOutstanding();
            if (outstanding.signum() <= 0) {
                continue;
            }
            AgingBucket worst = acc.worstBucket();
            if (bucketFilter != null && worst != bucketFilter) {
                continue;
            }
            Party party = partyById.get(acc.customerId);
            Customer customer = customerById.get(acc.customerId);
            BigDecimal limit = customer != null && customer.getCreditLimitAmount() != null
                ? customer.getCreditLimitAmount() : BigDecimal.ZERO;
            all.add(new DunningQueueRowDto(
                acc.customerId,
                party != null ? party.getUid() : null,
                party != null ? party.getName() : null,
                limit,
                outstanding,
                acc.oldestDaysOverdue,
                acc.oldestDueDate,
                worst,
                acc.overdueInvoiceCount
            ));
        }
        // Default sort: oldest-overdue desc (chase the worst first), then outstanding desc.
        all.sort(Comparator.comparing(
                (DunningQueueRowDto r) -> r.oldestDaysOverdue() == null ? Integer.MIN_VALUE : r.oldestDaysOverdue())
            .reversed()
            .thenComparing(DunningQueueRowDto::totalOutstanding, Comparator.reverseOrder()));

        int total = all.size();
        int from = (int) Math.min((long) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(all.subList(from, to), pageable, total);
    }

    // ---------------------------------------------------------------------
    // Customer drill-down (statement)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CustomerStatementDto customerStatement(String customerUid) {
        return buildStatement(requireCustomerByUid(customerUid));
    }

    // ---------------------------------------------------------------------
    // Credit-limit adjust (POST /api/v1/debt/customer/uid/{uid}/credit-limit)
    // ---------------------------------------------------------------------

    @Override
    @Transactional
    @Auditable(action = "ADJUST_CREDIT_LIMIT", entityType = "Customer")
    public CustomerStatementDto adjustCreditLimit(String customerUid, AdjustCreditLimitRequestDto request) {
        CustomerWithParty cwp = requireCustomerByUid(customerUid);
        BigDecimal oldLimit = cwp.customer.getCreditLimitAmount() != null
            ? cwp.customer.getCreditLimitAmount() : BigDecimal.ZERO;
        BigDecimal newLimit = request.newLimit();
        cwp.customer.setCreditLimitAmount(newLimit);

        Map<String, Object> payload = new HashMap<>();
        payload.put("customerId", cwp.customer.getPartyId());
        payload.put("partyUid", cwp.party.getUid());
        payload.put("oldLimit", oldLimit);
        payload.put("newLimit", newLimit);
        payload.put("reason", request.reason());
        payload.put("actorId", context.userId());
        events.publish("CustomerCreditLimitChanged.v1", "Customer",
            String.valueOf(cwp.customer.getPartyId()), payload);

        return buildStatement(cwp);
    }

    // ---------------------------------------------------------------------
    // Internals
    // ---------------------------------------------------------------------

    private CustomerStatementDto buildStatement(CustomerWithParty cwp) {
        LocalDate today = LocalDate.now();
        List<SalesInvoice> open = invoices.findOpenForCustomer(cwp.customer.getPartyId());
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        long overdueCount = 0;
        List<CustomerStatementDto.OpenInvoiceRow> openRows = new ArrayList<>(open.size());
        String currencyCode = null;
        for (SalesInvoice inv : open) {
            BigDecimal outstanding = inv.getTotalAmount().subtract(inv.getPaidAmount());
            totalOutstanding = totalOutstanding.add(outstanding);
            Integer daysOverdue = null;
            if (inv.getDueDate() != null && inv.getDueDate().isBefore(today)) {
                daysOverdue = (int) ChronoUnit.DAYS.between(inv.getDueDate(), today);
                overdueCount++;
            }
            if (currencyCode == null) currencyCode = inv.getCurrencyCode();
            openRows.add(new CustomerStatementDto.OpenInvoiceRow(
                inv.getId(),
                inv.getUid(),
                inv.getNumber(),
                inv.getInvoiceDate(),
                inv.getDueDate(),
                inv.getTotalAmount(),
                inv.getPaidAmount(),
                outstanding,
                daysOverdue,
                inv.getStatus()
            ));
        }
        // Recent receipts — capped at 50 newest-first.
        List<SalesReceipt> recent = receipts.findRecentForCustomer(
            cwp.customer.getPartyId(), PageRequest.of(0, 50));
        List<CustomerStatementDto.RecentReceiptRow> receiptRows = new ArrayList<>(recent.size());
        for (SalesReceipt r : recent) {
            receiptRows.add(new CustomerStatementDto.RecentReceiptRow(
                r.getId(),
                r.getUid(),
                r.getNumber(),
                r.getReceiptDate(),
                r.getPostedAt(),
                r.getTotalAmount(),
                r.getCurrencyCode()
            ));
            if (currencyCode == null) currencyCode = r.getCurrencyCode();
        }
        BigDecimal creditLimit = cwp.customer.getCreditLimitAmount() != null
            ? cwp.customer.getCreditLimitAmount() : BigDecimal.ZERO;
        BigDecimal utilisation = creditLimit.signum() > 0
            ? totalOutstanding.divide(creditLimit, DECIMAL_SCALE, RoundingMode.HALF_UP)
            : null;
        return new CustomerStatementDto(
            cwp.customer.getPartyId(),
            cwp.party.getUid(),
            cwp.party.getName(),
            currencyCode != null ? currencyCode : DEFAULT_CURRENCY,
            creditLimit,
            totalOutstanding,
            utilisation,
            openRows.size(),
            overdueCount,
            today,
            openRows,
            receiptRows
        );
    }

    private CustomerWithParty requireCustomerByUid(String customerUid) {
        Party party = parties.findByUid(customerUid)
            .orElseThrow(() -> new NoSuchElementException("Customer not found: " + customerUid));
        if (!Objects.equals(party.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Customer not found: " + customerUid);
        }
        Customer customer = customers.findById(party.getId())
            .orElseThrow(() -> new NoSuchElementException("Not a customer: " + customerUid));
        return new CustomerWithParty(customer, party);
    }

    private record CustomerWithParty(Customer customer, Party party) {}

    /**
     * Mutable per-customer accumulator used by {@link #aging} and {@link #dunning}.
     * Buckets per US-DEBT-003: CURRENT / 1-30 / 31-60 / 61-90 / 90+.
     */
    private static final class CustomerAgingAccumulator {
        final Long customerId;
        BigDecimal current = BigDecimal.ZERO;
        BigDecimal d1_30 = BigDecimal.ZERO;
        BigDecimal d31_60 = BigDecimal.ZERO;
        BigDecimal d61_90 = BigDecimal.ZERO;
        BigDecimal d90_plus = BigDecimal.ZERO;
        Integer oldestDaysOverdue;
        LocalDate oldestDueDate;
        long overdueInvoiceCount;
        Set<Long> invoiceIds = new HashSet<>();

        CustomerAgingAccumulator(Long customerId) {
            this.customerId = customerId;
        }

        void add(SalesInvoice inv, LocalDate today) {
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
            if (days <= 30)        d1_30   = d1_30.add(outstanding);
            else if (days <= 60)   d31_60  = d31_60.add(outstanding);
            else if (days <= 90)   d61_90  = d61_90.add(outstanding);
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
