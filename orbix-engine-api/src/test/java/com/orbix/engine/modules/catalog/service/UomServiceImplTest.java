package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UomDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateUomRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import com.orbix.engine.modules.catalog.repository.UomRepository;
import com.orbix.engine.modules.common.util.UidGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @InjectMocks private UomServiceImpl service;

    private static Uom uom(Long id, String code) {
        Uom uom = new Uom(code, "Name " + code, UomDimension.COUNT, true);
        uom.setId(id);
        ReflectionTestUtils.setField(uom, "uid", UidGenerator.next());
        return uom;
    }

    @Test
    void createUom_uppercasesCode() {
        when(uoms.existsByCode("KG")).thenReturn(false);
        when(uoms.save(any(Uom.class))).thenAnswer(inv -> {
            Uom u = inv.getArgument(0);
            u.setId(1L);
            ReflectionTestUtils.setField(u, "uid", UidGenerator.next());
            return u;
        });

        UomDto result = service.createUom(new CreateUomRequestDto(" kg ", "Kilogram", UomDimension.WEIGHT, true));

        ArgumentCaptor<Uom> saved = ArgumentCaptor.forClass(Uom.class);
        verify(uoms).save(saved.capture());
        assertThat(saved.getValue().getCode()).isEqualTo("KG");
        assertThat(result.dimension()).isEqualTo(UomDimension.WEIGHT);
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
    void updateUom_changesAttributes() {
        Uom existing = uom(1L, "KG");
        when(uoms.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        UomDto result = service.updateUomByUid(existing.getUid(),
            new UpdateUomRequestDto("Kilo", UomDimension.WEIGHT, false));

        assertThat(result.name()).isEqualTo("Kilo");
        assertThat(existing.getDimension()).isEqualTo(UomDimension.WEIGHT);
        assertThat(existing.isBase()).isFalse();
    }

    @Test
    void getUom_notFound_throwsNoSuchElement() {
        String missingUid = UidGenerator.next();
        when(uoms.findByUid(missingUid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUomByUid(missingUid))
            .isInstanceOf(NoSuchElementException.class);
    }
}
