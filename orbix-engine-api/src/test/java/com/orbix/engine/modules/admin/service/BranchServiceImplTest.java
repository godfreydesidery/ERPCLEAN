package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.BranchResponseDto;
import com.orbix.engine.modules.admin.domain.dto.CreateBranchRequestDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateBranchRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.BranchType;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.party.service.CustomerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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
class BranchServiceImplTest {

    private static final Long COMPANY_ID = 9L;
    private static final Long ACTOR_ID = 4L;

    @Mock private BranchRepository branches;
    @Mock private SectionRepository sections;
    @Mock private CustomerService customerService;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private BranchServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static Branch branch(Long id, Long companyId, String code) {
        Branch branch = new Branch(companyId, code, "Name " + code, BranchType.RETAIL,
            "Africa/Kampala", false, ACTOR_ID);
        branch.setId(id);
        ReflectionTestUtils.setField(branch, "uid", UidGenerator.next());
        return branch;
    }

    @Test
    void createBranch_uppercasesCode_savesBranchAndDefaultSection() {
        when(branches.existsByCompanyIdAndCode(COMPANY_ID, "DT")).thenReturn(false);
        when(branches.save(any(Branch.class))).thenAnswer(inv -> {
            Branch b = inv.getArgument(0);
            b.setId(50L);
            ReflectionTestUtils.setField(b, "uid", UidGenerator.next());
            return b;
        });

        BranchResponseDto result = service.createBranch(new CreateBranchRequestDto(
            " dt ", "Downtown", BranchType.RETAIL, "1 Main St", "0700", "Africa/Kampala"));

        assertThat(result.code()).isEqualTo("DT");
        assertThat(result.companyId()).isEqualTo(COMPANY_ID);
        assertThat(result.uid()).isNotBlank();

        ArgumentCaptor<Section> section = ArgumentCaptor.forClass(Section.class);
        verify(sections).save(section.capture());
        assertThat(section.getValue().getType()).isEqualTo(SectionType.RETAIL_FLOOR);
        assertThat(section.getValue().getBranchId()).isEqualTo(50L);
        verify(events).publish(eq("BranchCreated.v1"), any(), any(), any());
    }

    @Test
    void createBranch_rejectsDuplicateCode() {
        when(branches.existsByCompanyIdAndCode(COMPANY_ID, "DT")).thenReturn(true);

        assertThatThrownBy(() -> service.createBranch(new CreateBranchRequestDto(
            "DT", "Downtown", BranchType.RETAIL, null, null, "Africa/Kampala")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(branches, never()).save(any());
    }

    @Test
    void updateBranch_updatesDetails() {
        Branch existing = branch(50L, COMPANY_ID, "DT");
        when(branches.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        BranchResponseDto result = service.updateBranchByUid(existing.getUid(), new UpdateBranchRequestDto(
            "Downtown Mall", BranchType.WAREHOUSE, "2 New St", "0711", "Africa/Nairobi"));

        assertThat(result.name()).isEqualTo("Downtown Mall");
        assertThat(existing.getType()).isEqualTo(BranchType.WAREHOUSE);
        assertThat(existing.getTimeZone()).isEqualTo("Africa/Nairobi");
    }

    @Test
    void createBranch_warehouse_skipsSectionAndWalkInCustomer() {
        when(branches.existsByCompanyIdAndCode(COMPANY_ID, "WH")).thenReturn(false);
        when(branches.save(any(Branch.class))).thenAnswer(inv -> {
            Branch b = inv.getArgument(0);
            b.setId(60L);
            ReflectionTestUtils.setField(b, "uid", UidGenerator.next());
            return b;
        });

        service.createBranch(new CreateBranchRequestDto(
            "WH", "Central Warehouse", BranchType.WAREHOUSE, null, null, "Africa/Dar_es_Salaam"));

        verify(sections, never()).save(any());
        verify(customerService, never()).createWalkInCustomer(any());
    }

    @Test
    void createBranch_rejectsInvalidTimeZone() {
        when(branches.existsByCompanyIdAndCode(COMPANY_ID, "DT")).thenReturn(false);

        assertThatThrownBy(() -> service.createBranch(new CreateBranchRequestDto(
            "DT", "Downtown", BranchType.RETAIL, null, null, "Not/AZone")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid time zone");
        verify(branches, never()).save(any());
    }

    @Test
    void updateBranch_rejectsInvalidTimeZone() {
        Branch existing = branch(50L, COMPANY_ID, "DT");
        when(branches.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateBranchByUid(existing.getUid(), new UpdateBranchRequestDto(
            "Downtown", BranchType.RETAIL, null, null, "Not/AZone")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid time zone");
        assertThat(existing.getTimeZone()).isEqualTo("Africa/Kampala");
    }

    @Test
    void getBranch_fromAnotherCompany_throwsNotFound() {
        Branch foreign = branch(50L, 999L, "DT");
        when(branches.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getBranchByUid(foreign.getUid()))
            .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void deactivateBranch_setsInactive() {
        Branch existing = branch(50L, COMPANY_ID, "DT");
        when(branches.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        service.deactivateBranchByUid(existing.getUid(), "closing down");

        assertThat(existing.getStatus()).isEqualTo(AdminStatus.INACTIVE);
        verify(events).publish(eq("BranchDeactivated.v1"), any(), any(), any());
    }

    @Test
    void deactivateBranch_rejectsAlreadyInactiveBranch() {
        Branch existing = branch(50L, COMPANY_ID, "DT");
        existing.setStatus(AdminStatus.INACTIVE);
        when(branches.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.deactivateBranchByUid(existing.getUid(), "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already inactive");
    }

    @Test
    void deactivateBranch_rejectsDefaultBranch() {
        Branch dflt = new Branch(COMPANY_ID, "HQ", "Head Office", BranchType.RETAIL,
            "Africa/Kampala", true, ACTOR_ID);
        dflt.setId(50L);
        ReflectionTestUtils.setField(dflt, "uid", UidGenerator.next());
        when(branches.findByUid(dflt.getUid())).thenReturn(Optional.of(dflt));

        assertThatThrownBy(() -> service.deactivateBranchByUid(dflt.getUid(), "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("default branch");
        assertThat(dflt.getStatus()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void activateBranch_reactivatesInactiveBranch() {
        Branch existing = branch(50L, COMPANY_ID, "DT");
        existing.setStatus(AdminStatus.INACTIVE);
        when(branches.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        service.activateBranchByUid(existing.getUid(), "reopening");

        assertThat(existing.getStatus()).isEqualTo(AdminStatus.ACTIVE);
        verify(events).publish(eq("BranchActivated.v1"), any(), any(), any());
    }

    @Test
    void activateBranch_rejectsAlreadyActiveBranch() {
        Branch existing = branch(50L, COMPANY_ID, "DT");
        when(branches.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.activateBranchByUid(existing.getUid(), "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already active");
    }
}
