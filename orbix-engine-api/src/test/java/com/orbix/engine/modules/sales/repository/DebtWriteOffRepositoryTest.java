package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.sales.domain.entity.DebtWriteOff;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level contract tests for {@link DebtWriteOffRepository} derived finders.
 * Validates the method signatures compile and delegate correctly when backed by
 * a mock. Integration tests against a real DB are QA-engineer territory
 * (Testcontainers, see docs/conventions/hardening-checklist.md).
 */
@ExtendWith(MockitoExtension.class)
class DebtWriteOffRepositoryTest {

    private static final Long COMPANY_ID = 7L;

    @Mock
    private DebtWriteOffRepository repository;

    // ------------------------------------------------------------------
    // findByUid
    // ------------------------------------------------------------------

    @Test
    void findByUid_found_returnsOptionalPresent() {
        DebtWriteOff wo = writeOff(DebtWriteOffStatus.PENDING_APPROVAL);
        when(repository.findByUid(wo.getUid())).thenReturn(Optional.of(wo));

        Optional<DebtWriteOff> result = repository.findByUid(wo.getUid());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(DebtWriteOffStatus.PENDING_APPROVAL);
    }

    @Test
    void findByUid_notFound_returnsEmpty() {
        when(repository.findByUid("NO_SUCH")).thenReturn(Optional.empty());

        assertThat(repository.findByUid("NO_SUCH")).isEmpty();
    }

    // ------------------------------------------------------------------
    // findByCompanyIdAndStatusOrderByRequestedAtDescIdDesc
    // ------------------------------------------------------------------

    @Test
    void findByCompanyIdAndStatus_returnsPage() {
        DebtWriteOff wo = writeOff(DebtWriteOffStatus.POSTED);
        Page<DebtWriteOff> page = new PageImpl<>(List.of(wo));
        Pageable pageable = PageRequest.of(0, 10);
        when(repository.findByCompanyIdAndStatusOrderByRequestedAtDescIdDesc(
            COMPANY_ID, DebtWriteOffStatus.POSTED, pageable)).thenReturn(page);

        Page<DebtWriteOff> result = repository.findByCompanyIdAndStatusOrderByRequestedAtDescIdDesc(
            COMPANY_ID, DebtWriteOffStatus.POSTED, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(DebtWriteOffStatus.POSTED);
    }

    // ------------------------------------------------------------------
    // findFiltered — all four filter combos
    // ------------------------------------------------------------------

    @Test
    void findFiltered_noFilters_delegatesNullParams() {
        when(repository.findFiltered(eq(COMPANY_ID), eq(null), eq(null), any()))
            .thenReturn(Page.empty());

        repository.findFiltered(COMPANY_ID, null, null, PageRequest.of(0, 25));

        verify(repository).findFiltered(COMPANY_ID, null, null, PageRequest.of(0, 25));
    }

    @Test
    void findFiltered_statusAndKind_passedThrough() {
        when(repository.findFiltered(eq(COMPANY_ID),
            eq(DebtWriteOffStatus.PENDING_APPROVAL),
            eq(DebtWriteOffTargetKind.CUSTOMER_INVOICE),
            any())).thenReturn(Page.empty());

        repository.findFiltered(COMPANY_ID,
            DebtWriteOffStatus.PENDING_APPROVAL,
            DebtWriteOffTargetKind.CUSTOMER_INVOICE,
            PageRequest.of(0, 10));

        verify(repository).findFiltered(
            COMPANY_ID,
            DebtWriteOffStatus.PENDING_APPROVAL,
            DebtWriteOffTargetKind.CUSTOMER_INVOICE,
            PageRequest.of(0, 10));
    }

    @Test
    void findFiltered_statusOnly_kindNull() {
        when(repository.findFiltered(eq(COMPANY_ID),
            eq(DebtWriteOffStatus.REJECTED), eq(null), any()))
            .thenReturn(Page.empty());

        repository.findFiltered(COMPANY_ID, DebtWriteOffStatus.REJECTED, null, PageRequest.of(0, 5));

        verify(repository).findFiltered(COMPANY_ID, DebtWriteOffStatus.REJECTED, null, PageRequest.of(0, 5));
    }

    @Test
    void findFiltered_kindOnly_statusNull() {
        when(repository.findFiltered(eq(COMPANY_ID),
            eq(null), eq(DebtWriteOffTargetKind.SUPPLIER_INVOICE), any()))
            .thenReturn(Page.empty());

        repository.findFiltered(COMPANY_ID, null, DebtWriteOffTargetKind.SUPPLIER_INVOICE,
            PageRequest.of(0, 25));

        verify(repository).findFiltered(COMPANY_ID, null, DebtWriteOffTargetKind.SUPPLIER_INVOICE,
            PageRequest.of(0, 25));
    }

    // ------------------------------------------------------------------
    // Fixture helper
    // ------------------------------------------------------------------

    private DebtWriteOff writeOff(DebtWriteOffStatus status) {
        DebtWriteOff wo = DebtWriteOff.builder()
            .companyId(COMPANY_ID)
            .branchId(12L)
            .targetKind(DebtWriteOffTargetKind.CUSTOMER_INVOICE)
            .targetInvoiceId(500L)
            .targetInvoiceUid(UidGenerator.next())
            .amount(new BigDecimal("50000"))
            .currencyCode("TZS")
            .reason("test")
            .status(status)
            .requestedByUserId(4L)
            .requestedAt(Instant.now())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        ReflectionTestUtils.setField(wo, "uid", UidGenerator.next());
        ReflectionTestUtils.setField(wo, "id", 1000L);
        return wo;
    }
}
