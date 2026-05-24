package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.enums.BranchType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.entity.BusinessDayId;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import com.orbix.engine.modules.day.repository.BusinessDayRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessDayServiceImplTest {

    private static final Long COMPANY_ID = 5L;
    private static final Long BRANCH_ID = 12L;
    private static final Long ACTOR_ID = 3L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 14);

    @Mock private BusinessDayRepository businessDays;
    @Mock private BranchRepository branches;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    private BusinessDayServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch(COMPANY_ID)));
        // F7.5: BusinessDayServiceImpl auto-wires List<EodGuard>; pass empty
        // here so the tests focus on the state-machine without per-module
        // guard plumbing. EodGuard behaviour is unit-tested per-module.
        service = new BusinessDayServiceImpl(
            businessDays, branches, List.of(), events, context, branchScope);
    }

    private static Branch branch(Long companyId) {
        Branch branch = new Branch(companyId, "HQ", "Head Office", BranchType.RETAIL,
            "Africa/Kampala", true, ACTOR_ID);
        branch.setId(BRANCH_ID);
        return branch;
    }

    private static BusinessDay day(LocalDate date, BusinessDayStatus status) {
        BusinessDay day = new BusinessDay(BRANCH_ID, date, ACTOR_ID);
        day.setStatus(status);
        return day;
    }

    @Test
    void openDay_opensFreshDayAndEmitsEvent() {
        when(businessDays.findFirstByBranchIdAndStatusIn(eq(BRANCH_ID), any())).thenReturn(Optional.empty());
        when(businessDays.findByBranchIdOrderByBusinessDateDesc(BRANCH_ID)).thenReturn(List.of());
        when(businessDays.save(any(BusinessDay.class))).thenAnswer(inv -> inv.getArgument(0));

        BusinessDayDto result = service.openDay(BRANCH_ID, TODAY);

        assertThat(result.status()).isEqualTo(BusinessDayStatus.OPEN);
        assertThat(result.businessDate()).isEqualTo(TODAY);
        verify(events).publish(eq("BusinessDayOpened.v1"), any(), any(), any());
    }

    @Test
    void openDay_rejectsWhenANonClosedDayExists() {
        when(businessDays.findFirstByBranchIdAndStatusIn(eq(BRANCH_ID), any()))
            .thenReturn(Optional.of(day(TODAY.minusDays(1), BusinessDayStatus.OPEN)));

        assertThatThrownBy(() -> service.openDay(BRANCH_ID, TODAY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-closed business day");
        verify(businessDays, never()).save(any());
    }

    @Test
    void openDay_rejectsBackdatedDate() {
        when(businessDays.findFirstByBranchIdAndStatusIn(eq(BRANCH_ID), any())).thenReturn(Optional.empty());
        when(businessDays.findByBranchIdOrderByBusinessDateDesc(BRANCH_ID))
            .thenReturn(List.of(day(TODAY, BusinessDayStatus.CLOSED)));

        assertThatThrownBy(() -> service.openDay(BRANCH_ID, TODAY))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be after the latest day");
    }

    @Test
    void openDay_branchFromAnotherCompany_throwsNotFound() {
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch(999L)));

        assertThatThrownBy(() -> service.openDay(BRANCH_ID, TODAY))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void startClosing_movesOpenDayToClosing() {
        BusinessDay open = day(TODAY, BusinessDayStatus.OPEN);
        when(businessDays.findById(new BusinessDayId(BRANCH_ID, TODAY))).thenReturn(Optional.of(open));

        BusinessDayDto result = service.startClosing(BRANCH_ID, TODAY);

        assertThat(result.status()).isEqualTo(BusinessDayStatus.CLOSING);
        verify(events).publish(eq("BusinessDayClosingStarted.v1"), any(), any(), any());
    }

    @Test
    void startClosing_isIdempotentOnAlreadyClosingDay() {
        // F7.5: re-calling startClosing on a CLOSING day returns the existing
        // row without re-emitting an event (idempotent — TC-DAY-025).
        when(businessDays.findById(new BusinessDayId(BRANCH_ID, TODAY)))
            .thenReturn(Optional.of(day(TODAY, BusinessDayStatus.CLOSING)));

        BusinessDayDto result = service.startClosing(BRANCH_ID, TODAY);

        assertThat(result.status()).isEqualTo(BusinessDayStatus.CLOSING);
        verify(events, never()).publish(eq("BusinessDayClosingStarted.v1"), any(), any(), any());
    }

    @Test
    void closeDay_finalisesClosingDay() {
        BusinessDay closing = day(TODAY, BusinessDayStatus.CLOSING);
        when(businessDays.findById(new BusinessDayId(BRANCH_ID, TODAY))).thenReturn(Optional.of(closing));
        // F7.5 auto-roll: after CLOSING -> CLOSED, openInternal runs for
        // TODAY+1. Mock the lookups + save so the auto-roll path completes.
        when(businessDays.findById(new BusinessDayId(BRANCH_ID, TODAY.plusDays(1))))
            .thenReturn(Optional.empty());
        when(businessDays.findFirstByBranchIdAndStatusIn(eq(BRANCH_ID), any()))
            .thenReturn(Optional.empty());
        when(businessDays.findByBranchIdOrderByBusinessDateDesc(BRANCH_ID))
            .thenReturn(List.of(closing));
        when(businessDays.save(any(BusinessDay.class))).thenAnswer(inv -> inv.getArgument(0));

        BusinessDayDto result = service.closeDay(BRANCH_ID, TODAY, "eod/2026-05-14.pdf");

        assertThat(result.status()).isEqualTo(BusinessDayStatus.CLOSED);
        assertThat(result.closedBy()).isEqualTo(ACTOR_ID);
        assertThat(result.eodReportObjectKey()).isEqualTo("eod/2026-05-14.pdf");
        verify(events).publish(eq("BusinessDayClosed.v1"), any(), any(), any());
        // Auto-roll also fires a BusinessDayOpened.v1 for the next day.
        verify(events).publish(eq("BusinessDayOpened.v1"), any(), any(), any());
    }

    @Test
    void closeDay_rejectsDayThatIsNotClosing() {
        when(businessDays.findById(new BusinessDayId(BRANCH_ID, TODAY)))
            .thenReturn(Optional.of(day(TODAY, BusinessDayStatus.OPEN)));

        assertThatThrownBy(() -> service.closeDay(BRANCH_ID, TODAY, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must be CLOSING");
    }

    @Test
    void getCurrentDay_returnsNonClosedDay() {
        when(businessDays.findFirstByBranchIdAndStatusIn(eq(BRANCH_ID), any()))
            .thenReturn(Optional.of(day(TODAY, BusinessDayStatus.OPEN)));

        assertThat(service.getCurrentDay(BRANCH_ID)).isPresent();
    }
}
