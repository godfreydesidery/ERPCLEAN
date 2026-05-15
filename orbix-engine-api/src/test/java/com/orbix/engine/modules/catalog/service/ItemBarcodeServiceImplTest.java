package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemBarcodeRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemBarcodeDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
class ItemBarcodeServiceImplTest {

    private static final Long COMPANY_ID = 3L;
    private static final Long ACTOR_ID = 8L;

    @Mock private ItemBarcodeRepository barcodes;
    @Mock private ItemRepository items;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private ItemBarcodeServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static Item item(Long id, Long companyId) {
        Item item = new Item(companyId, "SKU" + id, "Item " + id, ItemType.SELLABLE,
            1L, 1L, 1L, ACTOR_ID);
        item.setId(id);
        return item;
    }

    private static Item weighedItem(Long id, Long companyId) {
        Item item = item(id, companyId);
        item.applyWeighing(true, WeighingUnit.KG, ACTOR_ID);
        return item;
    }

    @Test
    void addBarcode_savesAndPublishesEvent() {
        when(items.findById(1L)).thenReturn(Optional.of(item(1L, COMPANY_ID)));
        when(barcodes.existsByBarcode("5901234123457")).thenReturn(false);
        when(barcodes.save(any(ItemBarcode.class))).thenAnswer(inv -> {
            ItemBarcode b = inv.getArgument(0);
            b.setId(10L);
            return b;
        });

        ItemBarcodeDto result = service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("5901234123457", BarcodeType.EAN13, 2L, new BigDecimal("12")));

        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.barcode()).isEqualTo("5901234123457");
        assertThat(result.barcodeType()).isEqualTo(BarcodeType.EAN13);
        verify(events).publish(eq("BarcodeAdded.v1"), any(), any(), any());
    }

    @Test
    void addBarcode_rejectsDuplicateBarcode() {
        when(items.findById(1L)).thenReturn(Optional.of(item(1L, COMPANY_ID)));
        when(barcodes.existsByBarcode("5901234123457")).thenReturn(true);

        assertThatThrownBy(() -> service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("5901234123457", BarcodeType.EAN13, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already in use");
        verify(barcodes, never()).save(any());
    }

    @Test
    void addBarcode_rejectsBadEan13Length() {
        when(items.findById(1L)).thenReturn(Optional.of(item(1L, COMPANY_ID)));

        assertThatThrownBy(() -> service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("12345", BarcodeType.EAN13, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("EAN13 barcode must be 13 digits");
        verify(barcodes, never()).save(any());
    }

    @Test
    void addBarcode_embeddedWeight_savesPrefixForWeighedItem() {
        when(items.findById(1L)).thenReturn(Optional.of(weighedItem(1L, COMPANY_ID)));
        when(barcodes.existsByBarcode("2012345")).thenReturn(false);
        when(barcodes.save(any(ItemBarcode.class))).thenAnswer(inv -> {
            ItemBarcode b = inv.getArgument(0);
            b.setId(20L);
            return b;
        });

        ItemBarcodeDto result = service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("2012345", BarcodeType.EMBEDDED_WEIGHT, null, null));

        assertThat(result.barcode()).isEqualTo("2012345");
        assertThat(result.barcodeType()).isEqualTo(BarcodeType.EMBEDDED_WEIGHT);
    }

    @Test
    void addBarcode_embeddedWeight_rejectsNonWeighedItem() {
        when(items.findById(1L)).thenReturn(Optional.of(item(1L, COMPANY_ID)));

        assertThatThrownBy(() -> service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("2012345", BarcodeType.EMBEDDED_WEIGHT, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires a weighed item");
        verify(barcodes, never()).save(any());
    }

    @Test
    void addBarcode_embeddedWeight_rejectsBadPrefix() {
        when(items.findById(1L)).thenReturn(Optional.of(weighedItem(1L, COMPANY_ID)));

        assertThatThrownBy(() -> service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("3012345", BarcodeType.EMBEDDED_WEIGHT, null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("7 digits with leading '2'");
        verify(barcodes, never()).save(any());
    }

    @Test
    void addBarcode_itemFromAnotherCompany_throwsNotFound() {
        when(items.findById(1L)).thenReturn(Optional.of(item(1L, 999L)));

        assertThatThrownBy(() -> service.addBarcode(1L,
            new CreateItemBarcodeRequestDto("X", BarcodeType.EAN13, null, null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteBarcode_removesWhenItemInCompany() {
        ItemBarcode barcode = new ItemBarcode(1L, "X", BarcodeType.EAN13, null, BigDecimal.ONE);
        barcode.setId(10L);
        when(barcodes.findById(10L)).thenReturn(Optional.of(barcode));
        when(items.findById(1L)).thenReturn(Optional.of(item(1L, COMPANY_ID)));

        service.deleteBarcode(10L);

        verify(barcodes).delete(barcode);
    }

    @Test
    void deleteBarcode_notFound_throwsNoSuchElement() {
        when(barcodes.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBarcode(404L))
            .isInstanceOf(NoSuchElementException.class);
    }
}
