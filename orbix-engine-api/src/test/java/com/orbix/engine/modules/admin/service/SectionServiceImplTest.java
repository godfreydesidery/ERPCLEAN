package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.dto.SectionResponseDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateSectionRequestDto;
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
import com.orbix.engine.modules.pos.service.TillService;
import com.orbix.engine.modules.production.service.BomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SectionServiceImplTest {

    private static final Long COMPANY_ID = 9L;
    private static final Long ACTOR_ID = 4L;
    private static final Long BRANCH_ID = 50L;

    @Mock private SectionRepository sections;
    @Mock private BranchRepository branches;
    @Mock private TillService tillService;
    @Mock private BomService bomService;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private SectionServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private Branch activeBranch() {
        Branch branch = new Branch(COMPANY_ID, "DT", "Downtown", BranchType.RETAIL,
            "Africa/Kampala", false, ACTOR_ID);
        branch.setId(BRANCH_ID);
        ReflectionTestUtils.setField(branch, "uid", UidGenerator.next());
        return branch;
    }

    private static Section section(Long id, SectionType type, AdminStatus status) {
        Section section = new Section(BRANCH_ID, "S" + id, "Section " + id, type, ACTOR_ID);
        section.setId(id);
        section.setStatus(status);
        ReflectionTestUtils.setField(section, "uid", UidGenerator.next());
        return section;
    }

    @Test
    void createSection_savesUppercasedCode() {
        Branch branch = activeBranch();
        when(branches.findByUid(branch.getUid())).thenReturn(Optional.of(branch));
        when(sections.existsByBranchIdAndCode(BRANCH_ID, "BAKERY")).thenReturn(false);
        when(sections.save(any(Section.class))).thenAnswer(inv -> {
            Section s = inv.getArgument(0);
            s.setId(100L);
            ReflectionTestUtils.setField(s, "uid", UidGenerator.next());
            return s;
        });

        SectionResponseDto result = service.createSectionByBranchUid(branch.getUid(),
            new CreateSectionRequestDto(" bakery ", "Bakery", SectionType.BAKERY, null));

        assertThat(result.code()).isEqualTo("BAKERY");
        assertThat(result.type()).isEqualTo(SectionType.BAKERY);
        assertThat(result.uid()).isNotBlank();
    }

    @Test
    void createSection_rejectsDuplicateCode() {
        Branch branch = activeBranch();
        when(branches.findByUid(branch.getUid())).thenReturn(Optional.of(branch));
        when(sections.existsByBranchIdAndCode(BRANCH_ID, "BAKERY")).thenReturn(true);

        assertThatThrownBy(() -> service.createSectionByBranchUid(branch.getUid(),
            new CreateSectionRequestDto("BAKERY", "Bakery", SectionType.BAKERY, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(sections, never()).save(any());
    }

    @Test
    void createSection_rejectsInactiveBranch() {
        Branch branch = activeBranch();
        branch.setStatus(AdminStatus.INACTIVE);
        when(branches.findByUid(branch.getUid())).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> service.createSectionByBranchUid(branch.getUid(),
            new CreateSectionRequestDto("BAKERY", "Bakery", SectionType.BAKERY, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactive branch");
    }

    @Test
    void updateSection_updatesDetails() {
        Section existing = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));

        SectionResponseDto result = service.updateSectionByUid(existing.getUid(),
            new UpdateSectionRequestDto("Hot Bakery", SectionType.DELI, 7L));

        assertThat(result.name()).isEqualTo("Hot Bakery");
        assertThat(existing.getType()).isEqualTo(SectionType.DELI);
        assertThat(existing.getManagerUserId()).isEqualTo(7L);
    }

    @Test
    void deactivateSection_allowsNonRetailFloor() {
        Section bakery = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findByUid(bakery.getUid())).thenReturn(Optional.of(bakery));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));

        service.deactivateSectionByUid(bakery.getUid());

        assertThat(bakery.getStatus()).isEqualTo(AdminStatus.INACTIVE);
    }

    @Test
    void deactivateSection_allowsRetailFloorWhenAnotherActiveOneRemains() {
        Section target = section(100L, SectionType.RETAIL_FLOOR, AdminStatus.ACTIVE);
        Section other = section(101L, SectionType.RETAIL_FLOOR, AdminStatus.ACTIVE);
        when(sections.findByUid(target.getUid())).thenReturn(Optional.of(target));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(sections.findByBranchId(BRANCH_ID)).thenReturn(List.of(target, other));

        service.deactivateSectionByUid(target.getUid());

        assertThat(target.getStatus()).isEqualTo(AdminStatus.INACTIVE);
    }

    @Test
    void deactivateSection_rejectsLastActiveRetailFloor() {
        Section target = section(100L, SectionType.RETAIL_FLOOR, AdminStatus.ACTIVE);
        Section bakery = section(101L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findByUid(target.getUid())).thenReturn(Optional.of(target));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(sections.findByBranchId(BRANCH_ID)).thenReturn(List.of(target, bakery));

        assertThatThrownBy(() -> service.deactivateSectionByUid(target.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("last active RETAIL_FLOOR");
    }

    @Test
    void deactivateSection_rejectsAlreadyInactiveSection() {
        Section target = section(100L, SectionType.BAKERY, AdminStatus.INACTIVE);
        when(sections.findByUid(target.getUid())).thenReturn(Optional.of(target));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));

        assertThatThrownBy(() -> service.deactivateSectionByUid(target.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already inactive");
    }

    @Test
    void updateSection_notFound_throwsNoSuchElement() {
        String missingUid = UidGenerator.next();
        when(sections.findByUid(missingUid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSectionByUid(missingUid,
            new UpdateSectionRequestDto("x", SectionType.OTHER, null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deactivateSection_blockedWhenOpenTillSessionExists() {
        Section bakery = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findByUid(bakery.getUid())).thenReturn(Optional.of(bakery));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(tillService.hasOpenTillSessionsForBranch(BRANCH_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivateSectionByUid(bakery.getUid()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("OPEN till sessions");
        assertThat(bakery.getStatus()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void deactivateSection_blockedWhenActiveBomExists() {
        Section bakery = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findByUid(bakery.getUid())).thenReturn(Optional.of(bakery));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(tillService.hasOpenTillSessionsForBranch(BRANCH_ID)).thenReturn(false);
        when(bomService.hasActiveBomForSection(100L)).thenReturn(true);

        assertThatThrownBy(() -> service.deactivateSectionByUid(bakery.getUid()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACTIVE or DRAFT BOMs");
        assertThat(bakery.getStatus()).isEqualTo(AdminStatus.ACTIVE);
    }

    @Test
    void deactivateSection_allowedWhenNoOpenTillsAndNoActiveBoms() {
        Section bakery = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findByUid(bakery.getUid())).thenReturn(Optional.of(bakery));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(tillService.hasOpenTillSessionsForBranch(BRANCH_ID)).thenReturn(false);
        when(bomService.hasActiveBomForSection(100L)).thenReturn(false);

        service.deactivateSectionByUid(bakery.getUid());

        assertThat(bakery.getStatus()).isEqualTo(AdminStatus.INACTIVE);
    }
}
