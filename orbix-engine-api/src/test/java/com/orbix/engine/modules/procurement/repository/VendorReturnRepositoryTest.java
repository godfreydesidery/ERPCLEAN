package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.procurement.domain.entity.VendorReturn;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnReason;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level contract tests for {@link VendorReturnRepository} derived finders.
 * Validates method signatures compile and delegate correctly when backed by a mock.
 * Integration tests against a real DB are QA-engineer territory (Testcontainers).
 */
@ExtendWith(MockitoExtension.class)
class VendorReturnRepositoryTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID  = 12L;

    @Mock
    private VendorReturnRepository repository;

    @Test
    void findByUid_found_returnsOptionalPresent() {
        VendorReturn ret = vendorReturn();
        when(repository.findByUid(ret.getUid())).thenReturn(Optional.of(ret));

        Optional<VendorReturn> result = repository.findByUid(ret.getUid());

        assertThat(result).isPresent();
        assertThat(result.get().getStatus()).isEqualTo(VendorReturnStatus.DRAFT);
    }

    @Test
    void findByUid_notFound_returnsEmpty() {
        when(repository.findByUid("NO_SUCH")).thenReturn(Optional.empty());

        assertThat(repository.findByUid("NO_SUCH")).isEmpty();
    }

    @Test
    void existsByBranchIdAndNumber_delegates() {
        when(repository.existsByBranchIdAndNumber(BRANCH_ID, "RET-001")).thenReturn(true);

        assertThat(repository.existsByBranchIdAndNumber(BRANCH_ID, "RET-001")).isTrue();
        verify(repository).existsByBranchIdAndNumber(BRANCH_ID, "RET-001");
    }

    @Test
    void findByCompanyIdOrderByIdDesc_returnsPage() {
        VendorReturn ret = vendorReturn();
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByCompanyIdOrderByIdDesc(COMPANY_ID, pageable))
            .thenReturn(new PageImpl<>(List.of(ret)));

        Page<VendorReturn> page = repository.findByCompanyIdOrderByIdDesc(COMPANY_ID, pageable);

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findByCompanyIdAndBranchIdOrderByIdDesc_scopedByBranch() {
        VendorReturn ret = vendorReturn();
        Pageable pageable = PageRequest.of(0, 20);
        when(repository.findByCompanyIdAndBranchIdOrderByIdDesc(COMPANY_ID, BRANCH_ID, pageable))
            .thenReturn(new PageImpl<>(List.of(ret)));

        Page<VendorReturn> page = repository.findByCompanyIdAndBranchIdOrderByIdDesc(
            COMPANY_ID, BRANCH_ID, pageable);

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getBranchId()).isEqualTo(BRANCH_ID);
    }

    // ── fixture helper ──────────────────────────────────────────────────────────

    private VendorReturn vendorReturn() {
        VendorReturn r = new VendorReturn("RET-001", COMPANY_ID, BRANCH_ID, 808L,
            null, null, LocalDate.of(2026, 5, 28), VendorReturnReason.DAMAGED, true, null, 4L);
        ReflectionTestUtils.setField(r, "id", 1000L);
        ReflectionTestUtils.setField(r, "uid", UidGenerator.next());
        return r;
    }
}
