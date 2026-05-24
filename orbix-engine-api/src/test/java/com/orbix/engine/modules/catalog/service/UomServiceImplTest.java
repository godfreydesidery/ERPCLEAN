package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import com.orbix.engine.modules.catalog.repository.UomRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UomServiceImplTest {

    @Mock private UomRepository uoms;
    @Mock private RequestContext context;

    @InjectMocks private UomServiceImpl service;

    private static Uom uom(Long id, String code) {
        Uom uom = new Uom(code, "Name " + code, UomDimension.COUNT, true, 0L);
        uom.setId(id);
        ReflectionTestUtils.setField(uom, "uid", UidGenerator.next());
        return uom;
    }

    private void stubSaveAssigningIdAndUid() {
        when(uoms.save(any(Uom.class))).thenAnswer(inv -> {
            Uom u = inv.getArgument(0);
            u.setId(1L);
            ReflectionTestUtils.setField(u, "uid", UidGenerator.next());
            return u;
        });
    }

    @Test
    void createUom_uppercasesCode_andStampsActor() {
        when(uoms.existsByCode("KG")).thenReturn(false);
        when(context.userId()).thenReturn(7L);
        when(uoms.findByDimensionAndBaseTrue(UomDimension.WEIGHT)).thenReturn(List.of());
        stubSaveAssigningIdAndUid();

        UomDto result = service.createUom(new CreateUomRequestDto(" kg ", "Kilogram", UomDimension.WEIGHT, true));

        ArgumentCaptor<Uom> saved = ArgumentCaptor.forClass(Uom.class);
        verify(uoms).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("KG");
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(7L);
        assertThat(result.dimension()).isEqualTo(UomDimension.WEIGHT);
        assertThat(result.status()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(result.uid()).isNotBlank();
    }

    @Test
    void createUom_rejectsDuplicateCode() {
        when(uoms.existsByCode("EA")).thenReturn(true);

        assertThatThrownBy(() -> service.createUom(
            new CreateUomRequestDto("EA", "Each", UomDimension.COUNT, true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(uoms, never()).save(any());
    }

    @Test
    void createUom_asBase_demotesPreviousBaseInSameDimension() {
        Uom previousBase = new Uom("G", "Gram", UomDimension.WEIGHT, true, 0L);
        previousBase.setId(5L);
        when(uoms.existsByCode("KG")).thenReturn(false);
        when(context.userId()).thenReturn(1L);
        when(uoms.findByDimensionAndBaseTrue(UomDimension.WEIGHT)).thenReturn(List.of(previousBase));
        stubSaveAssigningIdAndUid();

        service.createUom(new CreateUomRequestDto("KG", "Kilogram", UomDimension.WEIGHT, true));

        assertThat(previousBase.isBase()).isFalse();
    }

    @Test
    void updateUom_changesAttributes() {
        Uom existing = uom(1L, "KG");
        when(uoms.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(context.userId()).thenReturn(2L);

        UomDto result = service.updateUomByUid(existing.getUid(),
            new UpdateUomRequestDto("Kilo", UomDimension.WEIGHT, false));

        assertThat(result.name()).isEqualTo("Kilo");
        assertThat(existing.getDimension()).isEqualTo(UomDimension.WEIGHT);
        assertThat(existing.isBase()).isFalse();
        assertThat(existing.getUpdatedBy()).isEqualTo(2L);
    }

    @Test
    void archiveUom_setsStatusArchived() {
        Uom existing = uom(1L, "EA");
        when(uoms.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(context.userId()).thenReturn(3L);

        service.archiveUomByUid(existing.getUid());

        assertThat(existing.getStatus()).isEqualTo(ItemStatus.ARCHIVED);
    }

    @Test
    void archiveUom_alreadyArchived_throws() {
        Uom existing = uom(1L, "EA");
        existing.archive(0L);
        when(uoms.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.archiveUomByUid(existing.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already archived");
    }

    @Test
    void activateUom_restoresArchivedToActive() {
        Uom existing = uom(1L, "EA");
        existing.archive(0L);
        when(uoms.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(context.userId()).thenReturn(4L);

        service.activateUomByUid(existing.getUid());

        assertThat(existing.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        assertThat(existing.getUpdatedBy()).isEqualTo(4L);
    }

    @Test
    void activateUom_alreadyActive_throws() {
        Uom existing = uom(1L, "EA");
        when(uoms.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.activateUomByUid(existing.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already active");
    }

    @Test
    void getUom_notFound_throwsNoSuchElement() {
        String missingUid = UidGenerator.next();
        when(uoms.findByUid(missingUid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUomByUid(missingUid))
            .isInstanceOf(NoSuchElementException.class);
    }
}
