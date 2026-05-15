package com.orbix.engine.modules.cash.service;

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
import com.orbix.engine.modules.common.service.EventPublisher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashLedgerServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 4L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 15);

    @Mock private CashEntryRepository entries;
    @Mock private CashBookRepository books;
    @Mock private EventPublisher events;

    @InjectMocks private CashLedgerServiceImpl service;

    private final AtomicLong nextId = new AtomicLong(50000);

    private CashEntry postIn(BigDecimal amount, String refType, Long refId) {
        return post(amount, CashDirection.IN, CashAccount.TILL, refType, refId);
    }

    private CashEntry post(BigDecimal amount, CashDirection direction,
                           CashAccount account, String refType, Long refId) {
        when(entries.findByRefTypeAndRefIdAndDirection(refType, refId, direction))
            .thenReturn(Optional.empty());
        when(entries.save(any(CashEntry.class))).thenAnswer(inv -> {
            CashEntry e = inv.getArgument(0);
            e.setId(nextId.getAndIncrement());
            return e;
        });
        when(books.findById(new CashBookId(BRANCH_ID, account, TODAY)))
            .thenReturn(Optional.empty());
        when(books.save(any(CashBook.class))).thenAnswer(inv -> inv.getArgument(0));

        service.post(Instant.now(), COMPANY_ID, BRANCH_ID, TODAY, account, direction, amount,
            "TZS", refType, refId, GlCategory.CASH, null, ACTOR_ID);

        ArgumentCaptor<CashEntry> captor = ArgumentCaptor.forClass(CashEntry.class);
        verify(entries).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void post_savesEntryAndEmitsBalanceEvent() {
        CashEntry saved = postIn(new BigDecimal("100"), CashRefType.POS_SALE_PAYMENT, 1L);

        assertThat(saved.getAmount()).isEqualByComparingTo("100");
        assertThat(saved.getDirection()).isEqualTo(CashDirection.IN);
        verify(events).publish(eq("CashEntryPosted.v1"), any(), any(), any());
        verify(events).publish(eq("CashBookBalanceUpdated.v1"), any(), any(), any());
    }

    @Test
    void post_idempotent_returnsExistingWithoutResaving() {
        CashEntry existing = new CashEntry(
            Instant.now(), COMPANY_ID, BRANCH_ID, TODAY,
            CashAccount.TILL, CashDirection.IN, new BigDecimal("100"),
            "TZS", CashRefType.POS_SALE_PAYMENT, 1L, GlCategory.CASH, null, ACTOR_ID);
        existing.setId(42L);
        when(entries.findByRefTypeAndRefIdAndDirection(CashRefType.POS_SALE_PAYMENT, 1L, CashDirection.IN))
            .thenReturn(Optional.of(existing));

        CashEntryDto result = service.post(Instant.now(), COMPANY_ID, BRANCH_ID, TODAY,
            CashAccount.TILL, CashDirection.IN, new BigDecimal("999"),
            "TZS", CashRefType.POS_SALE_PAYMENT, 1L, GlCategory.CASH, null, ACTOR_ID);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.amount()).isEqualByComparingTo("100");  // existing wins, not 999
        verify(entries, never()).save(any());
        verify(books, never()).save(any());
        verify(events, never()).publish(any(), any(), any(), any());
    }

    @Test
    void post_outDirection_decrementsCashBook() {
        ArgumentCaptor<CashBook> captor = ArgumentCaptor.forClass(CashBook.class);
        when(entries.findByRefTypeAndRefIdAndDirection(eq(CashRefType.SUPPLIER_PAYMENT), eq(2L), eq(CashDirection.OUT)))
            .thenReturn(Optional.empty());
        when(entries.save(any(CashEntry.class))).thenAnswer(inv -> {
            CashEntry e = inv.getArgument(0);
            e.setId(nextId.getAndIncrement());
            return e;
        });
        when(books.findById(new CashBookId(BRANCH_ID, CashAccount.CASH_BOX, TODAY)))
            .thenReturn(Optional.empty());
        when(books.save(any(CashBook.class))).thenAnswer(inv -> inv.getArgument(0));

        service.post(Instant.now(), COMPANY_ID, BRANCH_ID, TODAY,
            CashAccount.CASH_BOX, CashDirection.OUT, new BigDecimal("250"),
            "TZS", CashRefType.SUPPLIER_PAYMENT, 2L, GlCategory.SUPPLIER_SETTLEMENT, "SP-1", ACTOR_ID);

        verify(books).save(captor.capture());
        CashBook book = captor.getValue();
        assertThat(book.getInAmount()).isEqualByComparingTo("0");
        assertThat(book.getOutAmount()).isEqualByComparingTo("250");
        assertThat(book.getClosingAmount()).isEqualByComparingTo("-250");
    }

    @Test
    void post_zeroAmount_rejected() {
        when(entries.findByRefTypeAndRefIdAndDirection(any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.post(Instant.now(), COMPANY_ID, BRANCH_ID, TODAY,
            CashAccount.TILL, CashDirection.IN, BigDecimal.ZERO,
            "TZS", CashRefType.TILL_FLOAT, 1L, GlCategory.TILL_FLOAT, null, ACTOR_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("amount must be > 0");
        verify(entries, never()).save(any());
    }

    @Test
    void post_secondInOnSameBook_appliesIncrementally() {
        // First IN of 100 — book starts empty.
        when(entries.findByRefTypeAndRefIdAndDirection(CashRefType.POS_SALE_PAYMENT, 11L, CashDirection.IN))
            .thenReturn(Optional.empty());
        when(entries.save(any(CashEntry.class))).thenAnswer(inv -> {
            CashEntry e = inv.getArgument(0);
            e.setId(nextId.getAndIncrement());
            return e;
        });

        CashBookId id = new CashBookId(BRANCH_ID, CashAccount.TILL, TODAY);
        when(books.findById(id)).thenReturn(Optional.empty());
        when(books.save(any(CashBook.class))).thenAnswer(inv -> inv.getArgument(0));
        service.post(Instant.now(), COMPANY_ID, BRANCH_ID, TODAY,
            CashAccount.TILL, CashDirection.IN, new BigDecimal("100"), "TZS",
            CashRefType.POS_SALE_PAYMENT, 11L, GlCategory.CASH, null, ACTOR_ID);

        // Second IN of 200 — book now has the first entry's 100 in_amount.
        CashBook existingBook = new CashBook(id, COMPANY_ID, "TZS", BigDecimal.ZERO);
        existingBook.addIn(new BigDecimal("100"));
        when(books.findById(id)).thenReturn(Optional.of(existingBook));
        when(entries.findByRefTypeAndRefIdAndDirection(CashRefType.POS_SALE_PAYMENT, 12L, CashDirection.IN))
            .thenReturn(Optional.empty());

        service.post(Instant.now(), COMPANY_ID, BRANCH_ID, TODAY,
            CashAccount.TILL, CashDirection.IN, new BigDecimal("200"), "TZS",
            CashRefType.POS_SALE_PAYMENT, 12L, GlCategory.CASH, null, ACTOR_ID);

        assertThat(existingBook.getInAmount()).isEqualByComparingTo("300");
        assertThat(existingBook.getClosingAmount()).isEqualByComparingTo("300");
    }

    @Test
    void findByRef_propagatesEmpty() {
        when(entries.findByRefTypeAndRefIdAndDirection("X", 1L, CashDirection.IN))
            .thenReturn(Optional.empty());

        assertThat(service.findByRef("X", 1L, CashDirection.IN)).isEmpty();
    }
}
