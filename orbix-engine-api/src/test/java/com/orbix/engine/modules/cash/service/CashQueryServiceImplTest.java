package com.orbix.engine.modules.cash.service;

import com.orbix.engine.modules.cash.domain.dto.CashBookDto;
import com.orbix.engine.modules.cash.domain.dto.CashEntryDto;
import com.orbix.engine.modules.cash.domain.entity.CashBook;
import com.orbix.engine.modules.cash.domain.entity.CashBookId;
import com.orbix.engine.modules.cash.domain.entity.CashEntry;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.CashRefType;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.cash.repository.CashBookRepository;
import com.orbix.engine.modules.cash.repository.CashEntryRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.service.BranchScope;
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
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashQueryServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 4L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 15);

    @Mock private CashEntryRepository entries;
    @Mock private CashBookRepository books;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private CashQueryServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
    }

    private static CashEntry entry(Long companyId, Long branchId) {
        CashEntry e = new CashEntry(Instant.now(), companyId, branchId, TODAY,
            CashAccount.TILL, CashDirection.IN, new BigDecimal("100"),
            new BigDecimal("100"), BigDecimal.ONE, "TZS",
            CashRefType.POS_SALE_PAYMENT, 1L, GlCategory.CASH, null, ACTOR_ID);
        e.setId(7000L);
        ReflectionTestUtils.setField(e, "uid", UidGenerator.next());
        return e;
    }

    private static CashBook book(Long companyId, Long branchId) {
        CashBookId id = new CashBookId(branchId, CashAccount.TILL, "TZS", TODAY);
        CashBook b = new CashBook(id, companyId, new BigDecimal("50000"));
        ReflectionTestUtils.setField(b, "uid", UidGenerator.next());
        return b;
    }

    // ---- cash entry uid lookup ---------------------------------------------

    @Test
    void getCashEntryByUid_returnsEntryWhenTenantMatches() {
        CashEntry e = entry(COMPANY_ID, BRANCH_ID);
        when(entries.findByUid(e.getUid())).thenReturn(Optional.of(e));

        CashEntryDto result = service.getCashEntryByUid(e.getUid());

        assertThat(result.uid()).isEqualTo(e.getUid());
        assertThat(result.amount()).isEqualByComparingTo("100");
    }

    @Test
    void getCashEntryByUid_throwsWhenNotFound() {
        when(entries.findByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCashEntryByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Cash entry not found");
    }

    @Test
    void getCashEntryByUid_throwsWhenCrossTenant() {
        CashEntry foreign = entry(999L, BRANCH_ID);
        when(entries.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getCashEntryByUid(foreign.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ---- cash book uid lookup ----------------------------------------------

    @Test
    void getCashBookByUid_returnsBookWhenTenantMatches() {
        CashBook b = book(COMPANY_ID, BRANCH_ID);
        when(books.findByUid(b.getUid())).thenReturn(Optional.of(b));

        CashBookDto result = service.getCashBookByUid(b.getUid());

        assertThat(result.uid()).isEqualTo(b.getUid());
        assertThat(result.branchId()).isEqualTo(BRANCH_ID);
        assertThat(result.account()).isEqualTo(CashAccount.TILL);
    }

    @Test
    void getCashBookByUid_throwsWhenNotFound() {
        when(books.findByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCashBookByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Cash book not found");
    }

    @Test
    void getCashBookByUid_throwsWhenCrossTenant() {
        CashBook foreign = book(999L, BRANCH_ID);
        when(books.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getCashBookByUid(foreign.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
