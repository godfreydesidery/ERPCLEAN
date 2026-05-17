package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateVatGroupRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.VatGroupDto;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
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
class VatGroupServiceImplTest {

    private static final Long COMPANY_ID = 3L;
    private static final Long ACTOR_ID = 8L;

    @Mock private VatGroupRepository vatGroups;
    @Mock private RequestContext context;

    @InjectMocks private VatGroupServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static VatGroup vatGroup(Long id, String code, boolean isDefault) {
        VatGroup g = new VatGroup(COMPANY_ID, code, "Name " + code, new BigDecimal("0.1800"),
            LocalDate.of(2026, 1, 1), isDefault, ACTOR_ID);
        g.setId(id);
        ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
        return g;
    }

    @Test
    void createVatGroup_uppercasesCode() {
        when(vatGroups.existsByCompanyIdAndCode(COMPANY_ID, "STD")).thenReturn(false);
        when(vatGroups.save(any(VatGroup.class))).thenAnswer(inv -> {
            VatGroup g = inv.getArgument(0);
            g.setId(1L);
            ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
            return g;
        });

        VatGroupDto result = service.createVatGroup(new CreateVatGroupRequestDto(
            " std ", "Standard", new BigDecimal("0.1800"), LocalDate.of(2026, 1, 1), false));

        assertThat(result.code()).isEqualTo("STD");
        assertThat(result.rate()).isEqualByComparingTo("0.1800");
        assertThat(result.uid()).isNotBlank();
    }

    @Test
    void createVatGroup_rejectsDuplicateCode() {
        when(vatGroups.existsByCompanyIdAndCode(COMPANY_ID, "STD")).thenReturn(true);

        assertThatThrownBy(() -> service.createVatGroup(new CreateVatGroupRequestDto(
            "STD", "Standard", new BigDecimal("0.18"), LocalDate.of(2026, 1, 1), false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(vatGroups, never()).save(any());
    }

    @Test
    void createVatGroup_asDefault_clearsPreviousDefault() {
        VatGroup previous = vatGroup(5L, "OLD", true);
        when(vatGroups.existsByCompanyIdAndCode(COMPANY_ID, "STD")).thenReturn(false);
        when(vatGroups.save(any(VatGroup.class))).thenAnswer(inv -> {
            VatGroup g = inv.getArgument(0);
            g.setId(1L);
            ReflectionTestUtils.setField(g, "uid", UidGenerator.next());
            return g;
        });
        when(vatGroups.findByCompanyIdAndIsDefaultTrue(COMPANY_ID)).thenReturn(List.of(previous, vatGroup(1L, "STD", true)));

        service.createVatGroup(new CreateVatGroupRequestDto(
            "STD", "Standard", new BigDecimal("0.18"), LocalDate.of(2026, 1, 1), true));

        assertThat(previous.isDefault()).isFalse();
    }

    @Test
    void updateVatGroup_changesAttributes() {
        VatGroup existing = vatGroup(1L, "STD", false);
        when(vatGroups.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        VatGroupDto result = service.updateVatGroupByUid(existing.getUid(), new UpdateVatGroupRequestDto(
            "Standard rate", new BigDecimal("0.2000"), LocalDate.of(2026, 7, 1), false));

        assertThat(result.name()).isEqualTo("Standard rate");
        assertThat(existing.getRate()).isEqualByComparingTo("0.2000");
    }

    @Test
    void archiveVatGroup_setsArchivedStatus() {
        VatGroup existing = vatGroup(1L, "STD", false);
        when(vatGroups.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        service.archiveVatGroupByUid(existing.getUid());

        assertThat(existing.getStatus()).isEqualTo(ItemStatus.ARCHIVED);
    }

    @Test
    void archiveVatGroup_rejectsAlreadyArchived() {
        VatGroup existing = vatGroup(1L, "STD", false);
        existing.setStatus(ItemStatus.ARCHIVED);
        when(vatGroups.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.archiveVatGroupByUid(existing.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already archived");
    }

    @Test
    void getVatGroup_fromAnotherCompany_throwsNotFound() {
        VatGroup foreign = new VatGroup(999L, "X", "Foreign", new BigDecimal("0.1"),
            LocalDate.of(2026, 1, 1), false, ACTOR_ID);
        foreign.setId(7L);
        ReflectionTestUtils.setField(foreign, "uid", UidGenerator.next());
        when(vatGroups.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getVatGroupByUid(foreign.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
