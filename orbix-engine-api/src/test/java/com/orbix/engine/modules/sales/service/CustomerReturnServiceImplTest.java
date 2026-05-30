package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.day.service.DayGuard;
import com.orbix.engine.modules.iam.repository.AppUserRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.sales.domain.dto.CustomerCreditNoteDto;
import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus;
import com.orbix.engine.modules.sales.repository.CustomerCreditNoteAllocationRepository;
import com.orbix.engine.modules.sales.repository.CustomerCreditNoteRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnLineRepository;
import com.orbix.engine.modules.sales.repository.CustomerReturnRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import com.orbix.engine.modules.stock.service.StockMoveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CustomerReturnServiceImpl}.
 * Covers CCN-BUG-002: getCreditNote is implemented and wired.
 */
@ExtendWith(MockitoExtension.class)
class CustomerReturnServiceImplTest {

    private static final Long COMPANY_ID = 5L;
    private static final Long BRANCH_ID = 10L;
    private static final Long ACTOR_ID = 1L;
    private static final Long CUSTOMER_ID = 20L;

    @Mock private CustomerReturnRepository returns;
    @Mock private CustomerReturnLineRepository lines;
    @Mock private CustomerCreditNoteRepository creditNotes;
    @Mock private CustomerCreditNoteAllocationRepository allocations;
    @Mock private SalesInvoiceRepository invoices;
    @Mock private SalesInvoiceService salesInvoiceService;
    @Mock private ItemRepository items;
    @Mock private VatGroupRepository vatGroups;
    @Mock private AppUserRepository users;
    @Mock private StockMoveService stockMoveService;
    @Mock private DayGuard dayGuard;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private CustomerReturnServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    // -------------------------------------------------------------------------
    // CCN-BUG-002: GET /api/v1/customer-credit-notes/uid/{uid} was not wired;
    // the service implementation exists — verify it resolves and returns the DTO.
    // -------------------------------------------------------------------------

    @Test
    void getCreditNote_returnsHydratedDto() {
        CustomerCreditNote cn = creditNote("CN-001");
        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));
        when(allocations.findByCustomerCreditNoteIdOrderByAllocatedAtAsc(cn.getId()))
            .thenReturn(List.of());

        CustomerCreditNoteDto dto = service.getCreditNote(cn.getUid());

        assertThat(dto).isNotNull();
        assertThat(dto.uid()).isEqualTo(cn.getUid());
        assertThat(dto.number()).isEqualTo("CN-001");
        assertThat(dto.companyId()).isEqualTo(COMPANY_ID);
        assertThat(dto.status()).isEqualTo(CreditNoteStatus.POSTED);
        assertThat(dto.allocations()).isNotNull();
    }

    @Test
    void getCreditNote_unknownUid_throwsNoSuchElement() {
        when(creditNotes.findByUid("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCreditNote("missing"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Credit note not found");
    }

    @Test
    void getCreditNote_crossTenantUid_throwsNoSuchElement() {
        CustomerCreditNote cn = creditNote("CN-002");
        ReflectionTestUtils.setField(cn, "companyId", 9999L); // different tenant
        when(creditNotes.findByUid(cn.getUid())).thenReturn(Optional.of(cn));

        assertThatThrownBy(() -> service.getCreditNote(cn.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CustomerCreditNote creditNote(String number) {
        CustomerCreditNote cn = new CustomerCreditNote(
            number, COMPANY_ID, BRANCH_ID, CUSTOMER_ID,
            null, LocalDate.now(), "TZS",
            new BigDecimal("1000.0000"), null, ACTOR_ID);
        ReflectionTestUtils.setField(cn, "id", 100L);
        ReflectionTestUtils.setField(cn, "uid", UidGenerator.next());
        // Satisfy non-null DB constraints surfaced by reflection access during DTO mapping.
        cn.setAllocatedAmount(BigDecimal.ZERO);
        return cn;
    }
}
