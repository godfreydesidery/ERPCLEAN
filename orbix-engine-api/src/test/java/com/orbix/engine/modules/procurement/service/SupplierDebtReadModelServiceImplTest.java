package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.cash.domain.entity.SupplierPayment;
import com.orbix.engine.modules.cash.repository.SupplierPaymentRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.entity.Supplier;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.party.repository.SupplierRepository;
import com.orbix.engine.modules.procurement.domain.dto.SupplierAgingDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierDunningQueueRowDto;
import com.orbix.engine.modules.procurement.domain.dto.SupplierStatementDto;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoice;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import com.orbix.engine.modules.procurement.repository.SupplierInvoiceRepository;
import com.orbix.engine.modules.sales.domain.enums.AgingBucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SupplierDebtReadModelServiceImplTest {

    private static final Long COMPANY_ID = 9L;
    private static final Long BRANCH_ID = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 28);

    @Mock private SupplierInvoiceRepository invoices;
    @Mock private SupplierPaymentRepository payments;
    @Mock private SupplierRepository suppliers;
    @Mock private PartyRepository parties;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private SupplierDebtReadModelServiceImpl service;

    private long nextId = 1000L;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(branchScope.requireReadable(BRANCH_ID)).thenReturn(BRANCH_ID);
        lenient().when(branchScope.requireReadable(null)).thenReturn(null);
    }

    // ---------------------------------------------------------------------
    // aging()
    // ---------------------------------------------------------------------

    @Test
    void aging_singleSupplier_oneCurrentOneOverdue_bucketsCorrectly() {
        Party p1 = party(100L, "Acme Supplies");
        Supplier s1 = supplierOf(p1);

        SupplierInvoice currentInv = invoice(p1.getId(), TODAY.plusDays(5),
            new BigDecimal("200000"), BigDecimal.ZERO);
        SupplierInvoice overdue45 = invoice(p1.getId(), TODAY.minusDays(45),
            new BigDecimal("100000"), BigDecimal.ZERO);

        whenLoadOpen(currentInv, overdue45);
        whenLookupParties(p1);
        whenLookupSuppliers(s1);

        SupplierAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.asOf()).isEqualTo(TODAY);
        assertThat(dto.branchId()).isEqualTo(BRANCH_ID);
        assertThat(dto.rows()).hasSize(1);
        SupplierAgingDto.SupplierRow row = dto.rows().get(0);
        assertThat(row.supplierId()).isEqualTo(p1.getId());
        assertThat(row.supplierName()).isEqualTo("Acme Supplies");
        assertThat(row.current()).isEqualByComparingTo("200000");
        assertThat(row.d31_60()).isEqualByComparingTo("100000");
        assertThat(row.d1_30()).isEqualByComparingTo("0");
        assertThat(row.d61_90()).isEqualByComparingTo("0");
        assertThat(row.d90_plus()).isEqualByComparingTo("0");
        assertThat(row.totalOutstanding()).isEqualByComparingTo("300000");
        assertThat(row.oldestDaysOverdue()).isEqualTo(45);

        assertThat(dto.totals().current()).isEqualByComparingTo("200000");
        assertThat(dto.totals().d31_60()).isEqualByComparingTo("100000");
        assertThat(dto.totals().totalOutstanding()).isEqualByComparingTo("300000");
        assertThat(dto.totals().supplierCount()).isEqualTo(1L);
    }

    @Test
    void aging_allFiveBuckets_acrossOneSupplier() {
        Party p1 = party(101L, "All Buckets Supplier");
        Supplier s1 = supplierOf(p1);

        SupplierInvoice cur  = invoice(p1.getId(), TODAY.plusDays(10),  new BigDecimal("100"), BigDecimal.ZERO);
        SupplierInvoice o15  = invoice(p1.getId(), TODAY.minusDays(15), new BigDecimal("200"), BigDecimal.ZERO);
        SupplierInvoice o45  = invoice(p1.getId(), TODAY.minusDays(45), new BigDecimal("300"), BigDecimal.ZERO);
        SupplierInvoice o75  = invoice(p1.getId(), TODAY.minusDays(75), new BigDecimal("400"), BigDecimal.ZERO);
        SupplierInvoice o120 = invoice(p1.getId(), TODAY.minusDays(120), new BigDecimal("500"), BigDecimal.ZERO);

        whenLoadOpen(cur, o15, o45, o75, o120);
        whenLookupParties(p1);
        whenLookupSuppliers(s1);

        SupplierAgingDto dto = service.aging(BRANCH_ID, TODAY);

        SupplierAgingDto.SupplierRow row = dto.rows().get(0);
        assertThat(row.current()).isEqualByComparingTo("100");
        assertThat(row.d1_30()).isEqualByComparingTo("200");
        assertThat(row.d31_60()).isEqualByComparingTo("300");
        assertThat(row.d61_90()).isEqualByComparingTo("400");
        assertThat(row.d90_plus()).isEqualByComparingTo("500");
        assertThat(row.totalOutstanding()).isEqualByComparingTo("1500");
        assertThat(row.oldestDaysOverdue()).isEqualTo(120);
    }

    @Test
    void aging_multipleSuppliers_sortedByOldestOverdueDesc() {
        Party older = party(200L, "Older Supplier");
        Party newer = party(201L, "Newer Supplier");
        Supplier sOlder = supplierOf(older);
        Supplier sNewer = supplierOf(newer);

        SupplierInvoice newInv = invoice(newer.getId(), TODAY.minusDays(10),
            new BigDecimal("500"), BigDecimal.ZERO);
        SupplierInvoice oldInv = invoice(older.getId(), TODAY.minusDays(100),
            new BigDecimal("100"), BigDecimal.ZERO);

        whenLoadOpen(newInv, oldInv);
        whenLookupParties(older, newer);
        whenLookupSuppliers(sOlder, sNewer);

        SupplierAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.rows()).hasSize(2);
        assertThat(dto.rows().get(0).supplierName()).isEqualTo("Older Supplier");
        assertThat(dto.rows().get(1).supplierName()).isEqualTo("Newer Supplier");
    }

    @Test
    void aging_emptyCompany_returnsZeroTotals() {
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());

        SupplierAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.rows()).isEmpty();
        assertThat(dto.totals().totalOutstanding()).isEqualByComparingTo("0");
        assertThat(dto.totals().supplierCount()).isZero();
    }

    @Test
    void aging_paidInvoiceExcluded() {
        Party p1 = party(300L, "Paid Supplier");
        SupplierInvoice paid = invoice(p1.getId(), TODAY.minusDays(45),
            new BigDecimal("100"), new BigDecimal("100"));
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(paid));
        when(suppliers.findAllById(any())).thenReturn(List.of());
        when(parties.findAllById(any())).thenReturn(List.of());

        SupplierAgingDto dto = service.aging(BRANCH_ID, TODAY);

        assertThat(dto.rows()).isEmpty();
    }

    @Test
    void aging_companyWide_passesNullBranchToRepo() {
        when(invoices.findAllOpenForAging(COMPANY_ID, null)).thenReturn(List.of());

        SupplierAgingDto dto = service.aging(null, TODAY);

        assertThat(dto.branchId()).isNull();
    }

    // ---------------------------------------------------------------------
    // dunning()
    // ---------------------------------------------------------------------

    @Test
    void dunning_filtersByBucket_andSortsByOldestOverdueDesc() {
        Party p1 = party(400L, "Overdue 100d Supplier");
        Party p2 = party(401L, "Overdue 15d Supplier");

        SupplierInvoice o100 = invoice(p1.getId(), TODAY.minusDays(100), new BigDecimal("999"), BigDecimal.ZERO);
        SupplierInvoice o15  = invoice(p2.getId(), TODAY.minusDays(15),  new BigDecimal("111"), BigDecimal.ZERO);

        whenLoadOpen(o100, o15);
        whenLookupParties(p1, p2);

        Page<SupplierDunningQueueRowDto> all = service.dunning(BRANCH_ID, null, PageRequest.of(0, 25));
        assertThat(all.getContent()).hasSize(2);
        assertThat(all.getContent().get(0).supplierName()).isEqualTo("Overdue 100d Supplier");
        assertThat(all.getContent().get(0).worstBucket()).isEqualTo(AgingBucket.D_90_PLUS);
        assertThat(all.getContent().get(0).oldestDueDate()).isEqualTo(TODAY.minusDays(100));

        Page<SupplierDunningQueueRowDto> filtered = service.dunning(BRANCH_ID, AgingBucket.D_1_30, PageRequest.of(0, 25));
        assertThat(filtered.getContent()).hasSize(1);
        assertThat(filtered.getContent().get(0).supplierName()).isEqualTo("Overdue 15d Supplier");
    }

    @Test
    void dunning_emptyResult_returnsEmptyPage() {
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of());

        Page<SupplierDunningQueueRowDto> page = service.dunning(BRANCH_ID, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    // ---------------------------------------------------------------------
    // supplierStatement()
    // ---------------------------------------------------------------------

    @Test
    void supplierStatement_returnsOpenInvoices_andOutstandingBalance() {
        Party p1 = party(500L, "Statement Supplier");
        Supplier s1 = supplierOf(p1);

        SupplierInvoice open = invoice(p1.getId(), TODAY.minusDays(10),
            new BigDecimal("80000"), new BigDecimal("10000"));
        open.setNumber("SINV-001");

        when(parties.findByUid(p1.getUid())).thenReturn(Optional.of(p1));
        when(suppliers.findById(p1.getId())).thenReturn(Optional.of(s1));
        when(invoices.findOpenForSupplier(eq(p1.getId()), any(Pageable.class))).thenReturn(List.of(open));
        when(payments.findRecentPostedForSupplier(eq(p1.getId()), any(LocalDate.class), any(Pageable.class)))
            .thenReturn(List.of());

        SupplierStatementDto dto = service.supplierStatement(p1.getUid());

        assertThat(dto.supplierName()).isEqualTo("Statement Supplier");
        assertThat(dto.totalOutstanding()).isEqualByComparingTo("70000");
        assertThat(dto.openInvoices()).hasSize(1);
        assertThat(dto.openInvoices().get(0).number()).isEqualTo("SINV-001");
        assertThat(dto.openInvoices().get(0).outstanding()).isEqualByComparingTo("70000");
        // daysOverdue is computed by the service against LocalDate.now() (not the pinned TODAY
        // constant), so derive the expected value the same way to keep the assertion stable.
        int expectedDaysOverdue = (int) ChronoUnit.DAYS.between(TODAY.minusDays(10), LocalDate.now());
        assertThat(dto.openInvoices().get(0).daysOverdue()).isEqualTo(expectedDaysOverdue);
        assertThat(dto.openInvoiceCount()).isEqualTo(1L);
        assertThat(dto.overdueInvoiceCount()).isEqualTo(1L);
        assertThat(dto.agingRow()).isNotNull();
        assertThat(dto.agingRow().d1_30()).isEqualByComparingTo("70000");
    }

    @Test
    void supplierStatement_unknownUid_throws() {
        when(parties.findByUid("missing-uid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supplierStatement("missing-uid"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void supplierStatement_crossTenantParty_throwsNotFound() {
        Party other = party(600L, "Other tenant");
        ReflectionTestUtils.setField(other, "companyId", 9999L);
        when(parties.findByUid(other.getUid())).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.supplierStatement(other.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void supplierStatement_notASupplier_throws() {
        Party p1 = party(700L, "Not A Supplier");
        when(parties.findByUid(p1.getUid())).thenReturn(Optional.of(p1));
        when(suppliers.findById(p1.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.supplierStatement(p1.getUid()))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Not a supplier");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private Party party(Long id, String name) {
        Party p = new Party(COMPANY_ID, "SUPP-" + id, name, PartyCategory.BUSINESS, 1L);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "uid", UidGenerator.next());
        return p;
    }

    private Supplier supplierOf(Party party) {
        return new Supplier(party.getId());
    }

    private SupplierInvoice invoice(Long supplierId, LocalDate dueDate,
                                    BigDecimal total, BigDecimal paid) {
        long id = nextId++;
        SupplierInvoice inv = new SupplierInvoice(
            "SINV-" + id, "EXT-" + id, COMPANY_ID, BRANCH_ID, supplierId,
            TODAY.minusDays(60), dueDate, "TZS",
            total, BigDecimal.ZERO, null, 1L);
        ReflectionTestUtils.setField(inv, "id", id);
        ReflectionTestUtils.setField(inv, "uid", UidGenerator.next());
        inv.setTotalAmount(total);
        inv.setPaidAmount(paid);
        inv.setStatus(SupplierInvoiceStatus.POSTED);
        return inv;
    }

    private void whenLoadOpen(SupplierInvoice... invs) {
        when(invoices.findAllOpenForAging(COMPANY_ID, BRANCH_ID)).thenReturn(List.of(invs));
    }

    private void whenLookupSuppliers(Supplier... s) {
        when(suppliers.findAllById(any())).thenReturn(List.of(s));
    }

    private void whenLookupParties(Party... p) {
        when(parties.findAllById(any())).thenReturn(List.of(p));
    }
}
