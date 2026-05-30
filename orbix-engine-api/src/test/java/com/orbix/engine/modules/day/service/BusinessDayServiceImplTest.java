package com.orbix.engine.modules.day.service;

import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.enums.BranchType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.ResourceConflictException;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.day.domain.dto.BusinessDayDto;
import com.orbix.engine.modules.day.domain.dto.BusinessDayOverrideDto;
import com.orbix.engine.modules.day.domain.entity.BusinessDay;
import com.orbix.engine.modules.day.domain.entity.BusinessDayId;
import com.orbix.engine.modules.day.domain.entity.BusinessDayOverride;
import com.orbix.engine.modules.day.domain.enums.BusinessDayStatus;
import com.orbix.engine.modules.day.repository.BusinessDayOverrideRepository;
import com.orbix.engine.modules.day.repository.BusinessDayRepository;
import com.orbix.engine.modules.iam.service.BranchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock private BusinessDayOverrideRepository businessDayOverrides;
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
            businessDays, businessDayOverrides, branches, List.of(),
            events, context, branchScope);
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
        // Bypass @PrePersist — pin a stable uid so equality / payload
        // assertions are deterministic.
        ReflectionTestUtils.setField(day, "uid", UidGenerator.next());
        return day;
    }

    private static BusinessDayOverride override(Long branchId, LocalDate targetDate) {
        BusinessDayOverride o = new BusinessDayOverride(branchId, targetDate,
            "POS_SALE", 42L, "back-dated sales tally", ACTOR_ID);
        ReflectionTestUtils.setField(o, "id", 7L);
        ReflectionTestUtils.setField(o, "uid", UidGenerator.next());
        return o;
    }

    @Test
    void openDay_opensFreshDayAndEmitsEvent() {
        when(businessDays.findFirstByBranchIdAndStatusIn(eq(BRANCH_ID), any())).thenReturn(Optional.empty());
        when(businessDays.findByBranchIdOrderByBusinessDateDesc(BRANCH_ID)).thenReturn(List.of());
        when(businessDays.save(any(BusinessDay.class))).thenAnswer(inv -> {
            BusinessDay saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "uid", UidGenerator.next());
            return saved;
        });

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
            .isInstanceOf(ResourceConflictException.class)
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
        when(businessDays.save(any(BusinessDay.class))).thenAnswer(inv -> {
            BusinessDay saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "uid", UidGenerator.next());
            return saved;
        });

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

    // ---- uid entry points --------------------------------------------------

    @Test
    void getBusinessDayByUid_returnsDayWhenTenantMatches() {
        BusinessDay open = day(TODAY, BusinessDayStatus.OPEN);
        when(businessDays.findByUid(open.getUid())).thenReturn(Optional.of(open));

        BusinessDayDto result = service.getBusinessDayByUid(open.getUid());

        assertThat(result.uid()).isEqualTo(open.getUid());
        assertThat(result.status()).isEqualTo(BusinessDayStatus.OPEN);
    }

    @Test
    void getBusinessDayByUid_throwsWhenNotFound() {
        when(businessDays.findByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBusinessDayByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Business day not found");
    }

    @Test
    void getBusinessDayByUid_throwsWhenBranchBelongsToAnotherCompany() {
        BusinessDay open = day(TODAY, BusinessDayStatus.OPEN);
        when(businessDays.findByUid(open.getUid())).thenReturn(Optional.of(open));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch(999L)));

        assertThatThrownBy(() -> service.getBusinessDayByUid(open.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void startClosingByUid_movesOpenDayToClosing() {
        BusinessDay open = day(TODAY, BusinessDayStatus.OPEN);
        when(businessDays.findByUid(open.getUid())).thenReturn(Optional.of(open));

        BusinessDayDto result = service.startClosingByUid(open.getUid());

        assertThat(result.status()).isEqualTo(BusinessDayStatus.CLOSING);
        verify(events).publish(eq("BusinessDayClosingStarted.v1"), any(), any(), any());
    }

    // ---- business-day overrides --------------------------------------------

    @Test
    void postOverrideByDayUid_persistsAndEmitsEvent() {
        BusinessDay open = day(TODAY, BusinessDayStatus.OPEN);
        when(businessDays.findByUid(open.getUid())).thenReturn(Optional.of(open));
        when(businessDayOverrides.save(any(BusinessDayOverride.class))).thenAnswer(inv -> {
            BusinessDayOverride saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 99L);
            ReflectionTestUtils.setField(saved, "uid", UidGenerator.next());
            return saved;
        });

        BusinessDayOverrideDto result = service.postOverrideByDayUid(
            open.getUid(), "POS_SALE", 42L, "back-dated sales tally");

        assertThat(result.entityType()).isEqualTo("POS_SALE");
        assertThat(result.entityId()).isEqualTo(42L);
        assertThat(result.reason()).isEqualTo("back-dated sales tally");
        assertThat(result.archivedAt()).isNull();
        verify(events).publish(eq("BusinessDayOverridden.v1"), any(), any(), any());
    }

    @Test
    void postOverrideByDayUid_rejectsBlankReason() {
        // Bean-validation belongs to the controller; service guards as a
        // defence-in-depth check so internal producers can't smuggle blanks.
        BusinessDay open = day(TODAY, BusinessDayStatus.OPEN);

        assertThatThrownBy(() -> service.postOverrideByDayUid(open.getUid(), "POS_SALE", 1L, "  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason is required");
        verify(businessDayOverrides, never()).save(any());
    }

    @Test
    void archiveBusinessDayOverrideByUid_voidsTheGrant() {
        BusinessDayOverride o = override(BRANCH_ID, TODAY);
        when(businessDayOverrides.findByUid(o.getUid())).thenReturn(Optional.of(o));

        BusinessDayOverrideDto result = service.archiveBusinessDayOverrideByUid(o.getUid());

        assertThat(result.archivedAt()).isNotNull();
        assertThat(result.archivedBy()).isEqualTo(ACTOR_ID);
    }

    @Test
    void archiveBusinessDayOverrideByUid_rejectsDoubleArchive() {
        BusinessDayOverride o = override(BRANCH_ID, TODAY);
        o.archive(ACTOR_ID);
        when(businessDayOverrides.findByUid(o.getUid())).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.archiveBusinessDayOverrideByUid(o.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already archived");
    }

    @Test
    void archiveBusinessDayOverrideByUid_throwsWhenNotFound() {
        when(businessDayOverrides.findByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F"))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.archiveBusinessDayOverrideByUid("01HZ8X7M3K9PJK2D7Q5BCN8W4F"))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void archiveBusinessDayOverrideByUid_throwsOnCrossCompanyBranch() {
        BusinessDayOverride o = override(BRANCH_ID, TODAY);
        when(businessDayOverrides.findByUid(o.getUid())).thenReturn(Optional.of(o));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch(999L)));

        assertThatThrownBy(() -> service.archiveBusinessDayOverrideByUid(o.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
