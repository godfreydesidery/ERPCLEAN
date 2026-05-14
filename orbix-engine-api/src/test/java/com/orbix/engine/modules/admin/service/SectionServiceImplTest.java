package com.orbix.engine.modules.admin.service;

import com.orbix.engine.modules.admin.domain.dto.CreateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.dto.SectionResponseDto;
import com.orbix.engine.modules.admin.domain.dto.UpdateSectionRequestDto;
import com.orbix.engine.modules.admin.domain.entity.Branch;
import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.domain.enums.AdminStatus;
import com.orbix.engine.modules.admin.domain.enums.SectionType;
import com.orbix.engine.modules.admin.repository.BranchRepository;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private SectionServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private Branch activeBranch() {
        Branch branch = new Branch(COMPANY_ID, "DT", "Downtown", "RETAIL",
            "Africa/Kampala", false, ACTOR_ID);
        branch.setId(BRANCH_ID);
        return branch;
    }

    private static Section section(Long id, SectionType type, AdminStatus status) {
        Section section = new Section(BRANCH_ID, "S" + id, "Section " + id, type, ACTOR_ID);
        section.setId(id);
        section.setStatus(status);
        return section;
    }

    @Test
    void createSection_savesUppercasedCode() {
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(sections.existsByBranchIdAndCode(BRANCH_ID, "BAKERY")).thenReturn(false);
        when(sections.save(any(Section.class))).thenAnswer(inv -> {
            Section s = inv.getArgument(0);
            s.setId(100L);
            return s;
        });

        SectionResponseDto result = service.createSection(BRANCH_ID,
            new CreateSectionRequestDto(" bakery ", "Bakery", SectionType.BAKERY, null));

        assertThat(result.code()).isEqualTo("BAKERY");
        assertThat(result.type()).isEqualTo(SectionType.BAKERY);
    }

    @Test
    void createSection_rejectsDuplicateCode() {
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(sections.existsByBranchIdAndCode(BRANCH_ID, "BAKERY")).thenReturn(true);

        assertThatThrownBy(() -> service.createSection(BRANCH_ID,
            new CreateSectionRequestDto("BAKERY", "Bakery", SectionType.BAKERY, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(sections, never()).save(any());
    }

    @Test
    void createSection_rejectsInactiveBranch() {
        Branch branch = activeBranch();
        branch.setStatus(AdminStatus.INACTIVE);
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(branch));

        assertThatThrownBy(() -> service.createSection(BRANCH_ID,
            new CreateSectionRequestDto("BAKERY", "Bakery", SectionType.BAKERY, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inactive branch");
    }

    @Test
    void updateSection_updatesDetails() {
        Section existing = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findById(100L)).thenReturn(Optional.of(existing));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));

        SectionResponseDto result = service.updateSection(100L,
            new UpdateSectionRequestDto("Hot Bakery", SectionType.DELI, 7L));

        assertThat(result.name()).isEqualTo("Hot Bakery");
        assertThat(existing.getType()).isEqualTo(SectionType.DELI);
        assertThat(existing.getManagerUserId()).isEqualTo(7L);
    }

    @Test
    void deactivateSection_allowsNonRetailFloor() {
        Section bakery = section(100L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findById(100L)).thenReturn(Optional.of(bakery));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));

        service.deactivateSection(100L);

        assertThat(bakery.getStatus()).isEqualTo(AdminStatus.INACTIVE);
    }

    @Test
    void deactivateSection_allowsRetailFloorWhenAnotherActiveOneRemains() {
        Section target = section(100L, SectionType.RETAIL_FLOOR, AdminStatus.ACTIVE);
        Section other = section(101L, SectionType.RETAIL_FLOOR, AdminStatus.ACTIVE);
        when(sections.findById(100L)).thenReturn(Optional.of(target));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(sections.findByBranchId(BRANCH_ID)).thenReturn(List.of(target, other));

        service.deactivateSection(100L);

        assertThat(target.getStatus()).isEqualTo(AdminStatus.INACTIVE);
    }

    @Test
    void deactivateSection_rejectsLastActiveRetailFloor() {
        Section target = section(100L, SectionType.RETAIL_FLOOR, AdminStatus.ACTIVE);
        Section bakery = section(101L, SectionType.BAKERY, AdminStatus.ACTIVE);
        when(sections.findById(100L)).thenReturn(Optional.of(target));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));
        when(sections.findByBranchId(BRANCH_ID)).thenReturn(List.of(target, bakery));

        assertThatThrownBy(() -> service.deactivateSection(100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("last active RETAIL_FLOOR");
    }

    @Test
    void deactivateSection_rejectsAlreadyInactiveSection() {
        Section target = section(100L, SectionType.BAKERY, AdminStatus.INACTIVE);
        when(sections.findById(100L)).thenReturn(Optional.of(target));
        when(branches.findById(BRANCH_ID)).thenReturn(Optional.of(activeBranch()));

        assertThatThrownBy(() -> service.deactivateSection(100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already inactive");
    }

    @Test
    void updateSection_notFound_throwsNoSuchElement() {
        when(sections.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSection(404L,
            new UpdateSectionRequestDto("x", SectionType.OTHER, null)))
            .isInstanceOf(NoSuchElementException.class);
    }
}
