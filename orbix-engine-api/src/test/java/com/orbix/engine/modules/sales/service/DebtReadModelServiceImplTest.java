package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.sales.domain.dto.AdjustCreditLimitRequestDto;
import com.orbix.engine.modules.sales.domain.dto.CustomerStatementDto;
import com.orbix.engine.modules.sales.domain.dto.DebtAgingDto;
import com.orbix.engine.modules.sales.domain.dto.DunningQueueRowDto;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import com.orbix.engine.modules.sales.domain.enums.PaymentTerms;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.sales.repository.SalesReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DebtReadModelServiceImplTest {

    private static final Long COMPANY_ID = 9L;
    private static final Long BRANCH_ID = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 28);

    @Mock private SalesInvoiceRepository invoices;
    @Mock private SalesReceiptRepository receipts;
    @Mock private CustomerRepository customers;
    @Mock private PartyRepository parties;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;
    @Mock private EventPublisher events;

    @InjectMocks private DebtReadModelServiceImpl service;

    private long nextId = 1000L;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(7L);
        lenient().when(branchScope.requireReadable(BRANCH_ID)).thenReturn(BRANCH_ID);
        lenient().when(branchScope.requireReadable(null)).thenReturn(null);
    }

    // ---------------------------------------------------------------------
    // aging()
    // ---------------------------------------------------------------------

    @Test
    void aging_singleCustomer_oneCurrentOneOverdue_bucketsCorrectly() {
        Party p1 = party(100L, "Acme");
        Customer c1 = customerOf(p1, new BigDecimal("1000000"));

        SalesInvoice currentInv = invoice(p1.getId(), TODAY.plusDays(5),
            new BigDecimal("200000"), new BigDecimal("0"));
        SalesInvoice overdue45 = invoice(p1.getId(), TODAY.minusDays(45),
            new BigDecimal("100000"), new BigDecimal("0"));

        whenLoadOpen(currentInv, overdue45);
        whenLookupParties(p1);
        whenLookupCustomers(c1);

        DebtAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.asOf()).isEqualTo(TODAY);
        assertThat(dto.branchId()).isEqualTo(BRANCH_ID);
        assertThat(dto.rows()).hasSize(1);
        DebtAgingDto.CustomerRow row = dto.rows().get(0);
        assertThat(row.customerId()).isEqualTo(p1.getId());
        assertThat(row.customerName()).isEqualTo("Acme");
        assertThat(row.current()).isEqualByComparingTo("200000");
        assertThat(row.d31_60()).isEqualByComparingTo("100000");
        assertThat(row.d1_30()).isEqualByComparingTo("0");
        assertThat(row.d61_90()).isEqualByComparingTo("0");
        assertThat(row.d90_plus()).isEqualByComparingTo("0");
        assertThat(row.totalOutstanding()).isEqualByComparingTo("300000");
        assertThat(row.oldestDaysOverdue()).isEqualTo(45);
        assertThat(row.creditLimit()).isEqualByComparingTo("1000000");
        assertThat(row.creditUtilisation()).isNotNull();
        assertThat(row.creditUtilisation()).isEqualByComparingTo("0.3000");

        assertThat(dto.totals().current()).isEqualByComparingTo("200000");
        assertThat(dto.totals().d31_60()).isEqualByComparingTo("100000");
        assertThat(dto.totals().totalOutstanding()).isEqualByComparingTo("300000");
        assertThat(dto.totals().customerCount()).isEqualTo(1L);
    }

    @Test
    void aging_allFiveBuckets_acrossOneCustomer() {
        Party p1 = party(101L, "All Buckets");
        Customer c1 = customerOf(p1, BigDecimal.ZERO);

        SalesInvoice cur = invoice(p1.getId(), TODAY.plusDays(10), new BigDecimal("100"), BigDecimal.ZERO);
        SalesInvoice o15 = invoice(p1.getId(), TODAY.minusDays(15), new BigDecimal("200"), BigDecimal.ZERO);
        SalesInvoice o45 = invoice(p1.getId(), TODAY.minusDays(45), new BigDecimal("300"), BigDecimal.ZERO);
        SalesInvoice o75 = invoice(p1.getId(), TODAY.minusDays(75), new BigDecimal("400"), BigDecimal.ZERO);
        SalesInvoice o120 = invoice(p1.getId(), TODAY.minusDays(120), new BigDecimal("500"), BigDecimal.ZERO);

        whenLoadOpen(cur, o15, o45, o75, o120);
        whenLookupParties(p1);
        whenLookupCustomers(c1);

        DebtAgingDto dto = service.aging(BRANCH_ID, TODAY);

        DebtAgingDto.CustomerRow row = dto.rows().get(0);
        assertThat(row.current()).isEqualByComparingTo("100");
        assertThat(row.d1_30()).isEqualByComparingTo("200");
        assertThat(row.d31_60()).isEqualByComparingTo("300");
        assertThat(row.d61_90()).isEqualByComparingTo("400");
        assertThat(row.d90_plus()).isEqualByComparingTo("500");
        assertThat(row.totalOutstanding()).isEqualByComparingTo("1500");
        assertThat(row.oldestDaysOverdue()).isEqualTo(120);
        // No credit limit → utilisation null.
        assertThat(row.creditUtilisation()).isNull();
    }

    @Test
    void aging_multipleCustomers_sortedByOldestOverdueDesc() {
        Party older = party(200L, "Older");
        Party newer = party(201L, "Newer");
        Customer cOlder = customerOf(older, BigDecimal.ZERO);
        Customer cNewer = customerOf(newer, BigDecimal.ZERO);

        SalesInvoice newInv = invoice(newer.getId(), TODAY.minusDays(10),
            new BigDecimal("500"), BigDecimal.ZERO);
        SalesInvoice oldInv = invoice(older.getId(), TODAY.minusDays(100),
            new BigDecimal("100"), BigDecimal.ZERO);

        whenLoadOpen(newInv, oldInv);
        whenLookupParties(older, newer);
        whenLookupCustomers(cOlder, cNewer);

        DebtAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.rows()).hasSize(2);
        assertThat(dto.rows().get(0).customerName()).isEqualTo("Older");
        assertThat(dto.rows().get(1).customerName()).isEqualTo("Newer");
    }

    @Test
    void aging_emptyCompany_returnsZeroTotals() {
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());

        DebtAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.rows()).isEmpty();
        assertThat(dto.totals().totalOutstanding()).isEqualByComparingTo("0");
        assertThat(dto.totals().customerCount()).isZero();
    }

    @Test
    void aging_paidInvoiceExcluded() {
        Party p1 = party(300L, "Paid Co");
        SalesInvoice paid = invoice(p1.getId(), TODAY.minusDays(45),
            new BigDecimal("100"), new BigDecimal("100"));
        // Repository filter is "totalAmount > paidAmount" so paid is excluded
        // already — but service must defensively guard too.
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(paid));
        when(customers.findAllById(any())).thenReturn(List.of());
        when(parties.findAllById(any())).thenReturn(List.of());

        DebtAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.rows()).isEmpty();
    }

    @Test
    void aging_companyWide_passesNullBranchToRepo() {
        when(invoices.findAllOpenForAging(COMPANY_ID, null)).thenReturn(List.of());

        DebtAgingDto dto = service.aging(null, TODAY);

        assertThat(dto.branchId()).isNull();
    }

    // ---------------------------------------------------------------------
    // dunning()
    // ---------------------------------------------------------------------

    @Test
    void dunning_filtersByBucket_andSortsByOldestOverdueDesc() {
        Party p1 = party(400L, "Overdue 100d");
        Party p2 = party(401L, "Overdue 15d");
        Customer c1 = customerOf(p1, BigDecimal.ZERO);
        Customer c2 = customerOf(p2, BigDecimal.ZERO);

        SalesInvoice o100 = invoice(p1.getId(), TODAY.minusDays(100), new BigDecimal("999"), BigDecimal.ZERO);
        SalesInvoice o15 = invoice(p2.getId(), TODAY.minusDays(15), new BigDecimal("111"), BigDecimal.ZERO);

        whenLoadOpen(o100, o15);
        whenLookupParties(p1, p2);
        whenLookupCustomers(c1, c2);

        Page<DunningQueueRowDto> all = service.dunning(BRANCH_ID, null, PageRequest.of(0, 25));
        assertThat(all.getContent()).hasSize(2);
        assertThat(all.getContent().get(0).customerName()).isEqualTo("Overdue 100d");
        assertThat(all.getContent().get(0).worstBucket()).isEqualTo(AgingBucket.D_90_PLUS);
        assertThat(all.getContent().get(0).oldestDueDate()).isEqualTo(TODAY.minusDays(100));

        Page<DunningQueueRowDto> filtered = service.dunning(BRANCH_ID, AgingBucket.D_1_30, PageRequest.of(0, 25));
        assertThat(filtered.getContent()).hasSize(1);
        assertThat(filtered.getContent().get(0).customerName()).isEqualTo("Overdue 15d");
    }

    @Test
    void dunning_emptyResult_returnsEmptyPage() {
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());

        Page<DunningQueueRowDto> page = service.dunning(BRANCH_ID, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ---------------------------------------------------------------------
    // customerStatement()
    // ---------------------------------------------------------------------

    @Test
    void customerStatement_returnsOpenInvoices_andOutstandingBalance() {
        Party p1 = party(500L, "Statement Co");
        Customer c1 = customerOf(p1, new BigDecimal("500000"));

        SalesInvoice open = invoice(p1.getId(), TODAY.minusDays(10),
            new BigDecimal("80000"), new BigDecimal("10000"));
        open.setNumber("INV-001");

        when(parties.findByUid(p1.getUid())).thenReturn(Optional.of(p1));
        when(customers.findById(p1.getId())).thenReturn(Optional.of(c1));
        when(invoices.findOpenForCustomer(p1.getId())).thenReturn(List.of(open));
        when(receipts.findRecentForCustomer(eq(p1.getId()), any(Pageable.class))).thenReturn(List.of());

        CustomerStatementDto dto = service.customerStatement(p1.getUid());

        assertThat(dto.customerName()).isEqualTo("Statement Co");
        assertThat(dto.creditLimit()).isEqualByComparingTo("500000");
        assertThat(dto.totalOutstanding()).isEqualByComparingTo("70000");
        assertThat(dto.creditUtilisation()).isNotNull();
        assertThat(dto.openInvoices()).hasSize(1);
        assertThat(dto.openInvoices().get(0).number()).isEqualTo("INV-001");
        assertThat(dto.openInvoices().get(0).outstanding()).isEqualByComparingTo("70000");
        assertThat(dto.openInvoices().get(0).daysOverdue()).isEqualTo(10);
        assertThat(dto.openInvoiceCount()).isEqualTo(1L);
        assertThat(dto.overdueInvoiceCount()).isEqualTo(1L);
    }

    @Test
    void customerStatement_unknownUid_throws() {
        when(parties.findByUid("missing-uid"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.customerStatement("missing-uid"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void customerStatement_crossTenantParty_throwsNotFound() {
        Party other = party(600L, "Other tenant");
        ReflectionTestUtils.setField(other, "companyId", 9999L);
        when(parties.findByUid(other.getUid())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.customerStatement(other.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ---------------------------------------------------------------------
    // adjustCreditLimit()
    // ---------------------------------------------------------------------

    @Test
    void adjustCreditLimit_persists_andEmitsOutboxEvent() {
        Party p1 = party(700L, "Adjust");
        Customer c1 = customerOf(p1, new BigDecimal("100000"));

        when(parties.findByUid(p1.getUid())).thenReturn(Optional.of(p1));
        when(customers.findById(p1.getId())).thenReturn(Optional.of(c1));
        when(invoices.findOpenForCustomer(p1.getId())).thenReturn(List.of());
        when(receipts.findRecentForCustomer(eq(p1.getId()), any(Pageable.class))).thenReturn(List.of());

        AdjustCreditLimitRequestDto req = new AdjustCreditLimitRequestDto(
            new BigDecimal("250000.0000"), "Raised after quarterly review");

        CustomerStatementDto dto = service.adjustCreditLimit(p1.getUid(), req);

        assertThat(dto.creditLimit()).isEqualByComparingTo("250000.0000");
        assertThat(c1.getCreditLimitAmount()).isEqualByComparingTo("250000.0000");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(events).publish(eq("CustomerCreditLimitChanged.v1"),
            eq("Customer"), eq(String.valueOf(p1.getId())), payload.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> p = (Map<String, Object>) payload.getValue();
        assertThat(p).containsKeys("customerId", "oldLimit", "newLimit", "actorId", "reason");
        assertThat(p.get("oldLimit")).isEqualTo(new BigDecimal("100000"));
        assertThat(p.get("newLimit")).isEqualTo(new BigDecimal("250000.0000"));
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Party party(Long id, String name) {
        Party p = new Party(COMPANY_ID, "CUST-" + id, name, PartyCategory.INDIVIDUAL, 1L);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "uid", UidGenerator.next());
        return p;
    }

    private Customer customerOf(Party party, BigDecimal limit) {
        Customer c = new Customer(party.getId());
        c.setCreditLimitAmount(limit);
        return c;
    }

    private SalesInvoice invoice(Long customerId, LocalDate dueDate,
                                 BigDecimal total, BigDecimal paid) {
        long id = nextId++;
        SalesInvoice inv = new SalesInvoice(
            "INV-" + id, COMPANY_ID, BRANCH_ID, customerId, null,
            TODAY.minusDays(60), dueDate, PaymentTerms.CREDIT, "TZS", 1L, null, null, 1L);
        ReflectionTestUtils.setField(inv, "id", id);
        ReflectionTestUtils.setField(inv, "uid", UidGenerator.next());
        inv.setTotalAmount(total);
        inv.setPaidAmount(paid);
        inv.setStatus(SalesInvoiceStatus.POSTED);
        return inv;
    }

    private void whenLoadOpen(SalesInvoice... invs) {
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(invs));
    }

    private void whenLookupCustomers(Customer... c) {
        when(customers.findAllById(any())).thenReturn(List.of(c));
    }

    private void whenLookupParties(Party... p) {
        when(parties.findAllById(any())).thenReturn(List.of(p));
    }
}
