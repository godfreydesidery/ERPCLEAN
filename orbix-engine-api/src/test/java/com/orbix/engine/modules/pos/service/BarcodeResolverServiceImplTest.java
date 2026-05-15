package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.dto.ResolvedBarcodeDto;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BarcodeResolverServiceImplTest {

    private static final Long COMPANY_ID = 3L;
    private static final Long ACTOR_ID = 8L;

    @Mock private ItemBarcodeRepository barcodes;
    @Mock private ItemRepository items;
    @Mock private RequestContext context;

    @InjectMocks private BarcodeResolverServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
    }

    private static Item plainItem() {
        Item item = new Item(COMPANY_ID, "SKU1", "Item 1", ItemType.SELLABLE,
            1L, 1L, 1L, ACTOR_ID);
        item.setId(1L);
        return item;
    }

    private static Item weighedItem() {
        Item item = new Item(COMPANY_ID, "TOMATO-LOOSE", "Tomato loose", ItemType.SELLABLE,
            1L, 7L, 1L, ACTOR_ID);
        item.setId(42L);
        item.applyWeighing(true, WeighingUnit.KG, ACTOR_ID);
        return item;
    }

    private static ItemBarcode plainBarcode() {
        ItemBarcode b = new ItemBarcode(1L, "5901234123457", BarcodeType.EAN13, null, BigDecimal.ONE);
        b.setId(100L);
        return b;
    }

    private static ItemBarcode embeddedBarcode() {
        ItemBarcode b = new ItemBarcode(42L, "2012345", BarcodeType.EMBEDDED_WEIGHT, null, BigDecimal.ONE);
        b.setId(200L);
        return b;
    }

    @Test
    void resolve_plainBarcode_returnsItemWithPackQty() {
        when(barcodes.findByBarcode("5901234123457")).thenReturn(Optional.of(plainBarcode()));
        when(items.findById(1L)).thenReturn(Optional.of(plainItem()));

        ResolvedBarcodeDto result = service.resolve("5901234123457");

        assertThat(result.itemId()).isEqualTo(1L);
        assertThat(result.qty()).isEqualByComparingTo("1");
        assertThat(result.barcodeType()).isEqualTo(BarcodeType.EAN13);
        assertThat(result.weighed()).isFalse();
    }

    @Test
    void resolve_embeddedWeight_matchesPrefixAndDecodesWeight() {
        // 2 + 012345 + 00420 + check digit (8). 420g / 1000 = 0.420 kg.
        when(barcodes.findByBarcode("2012345004208")).thenReturn(Optional.empty());
        when(barcodes.findByBarcodeAndBarcodeType("2012345", BarcodeType.EMBEDDED_WEIGHT))
            .thenReturn(Optional.of(embeddedBarcode()));
        when(items.findById(42L)).thenReturn(Optional.of(weighedItem()));

        ResolvedBarcodeDto result = service.resolve("2012345004208");

        assertThat(result.itemId()).isEqualTo(42L);
        assertThat(result.qty()).isEqualByComparingTo("0.420");
        assertThat(result.barcodeType()).isEqualTo(BarcodeType.EMBEDDED_WEIGHT);
        assertThat(result.weighed()).isTrue();
        assertThat(result.weighingUnit()).isEqualTo(WeighingUnit.KG);
    }

    @Test
    void resolve_embeddedWeight_zeroWeight_throws() {
        when(barcodes.findByBarcode("2012345000000")).thenReturn(Optional.empty());
        when(barcodes.findByBarcodeAndBarcodeType("2012345", BarcodeType.EMBEDDED_WEIGHT))
            .thenReturn(Optional.of(embeddedBarcode()));

        assertThatThrownBy(() -> service.resolve("2012345000000"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zero qty");
    }

    @Test
    void resolve_embeddedWeight_unknownPlu_throws() {
        when(barcodes.findByBarcode("2999999004208")).thenReturn(Optional.empty());
        when(barcodes.findByBarcodeAndBarcodeType("2999999", BarcodeType.EMBEDDED_WEIGHT))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("2999999004208"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("No EMBEDDED_WEIGHT barcode matches");
    }

    @Test
    void resolve_unknownPlainBarcode_throws() {
        when(barcodes.findByBarcode("99999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("99999"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Barcode not found");
    }

    @Test
    void resolve_archivedItem_throws() {
        Item archived = plainItem();
        archived.archive(ACTOR_ID);
        when(barcodes.findByBarcode("5901234123457")).thenReturn(Optional.of(plainBarcode()));
        when(items.findById(1L)).thenReturn(Optional.of(archived));

        assertThatThrownBy(() -> service.resolve("5901234123457"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not sellable at the till");
    }

    @Test
    void resolve_crossCompanyItem_throwsNotFound() {
        ItemBarcode barcode = plainBarcode();
        Item otherCompany = new Item(999L, "SKU1", "Item 1", ItemType.SELLABLE,
            1L, 1L, 1L, ACTOR_ID);
        otherCompany.setId(1L);
        when(barcodes.findByBarcode("5901234123457")).thenReturn(Optional.of(barcode));
        when(items.findById(1L)).thenReturn(Optional.of(otherCompany));

        assertThatThrownBy(() -> service.resolve("5901234123457"))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("Item not found");
    }

    @Test
    void resolve_blankCode_throws() {
        assertThatThrownBy(() -> service.resolve("  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scannedCode is required");
    }
}
