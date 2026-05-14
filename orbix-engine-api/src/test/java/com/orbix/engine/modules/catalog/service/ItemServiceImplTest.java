package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.ItemResponseDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdateItemRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.WeighingUnit;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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
class ItemServiceImplTest {

    private static final Long COMPANY_ID = 2L;
    private static final Long ACTOR_ID = 5L;

    @Mock private ItemRepository repo;
    @Mock private ItemBarcodeRepository barcodes;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private ItemServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static Item item(Long id, ItemStatus status) {
        Item item = new Item(COMPANY_ID, "SKU" + id, "Item " + id, ItemType.SELLABLE,
            10L, 20L, 30L, ACTOR_ID);
        item.setId(id);
        item.setStatus(status);
        return item;
    }

    /** A plain (not weighed, not batch-tracked) edit payload. */
    private static UpdateItemRequestDto plainUpdate(String name) {
        return new UpdateItemRequestDto(name, "Sugar", ItemType.BOTH, 11L, 21L, 31L,
            false, new BigDecimal("4500"), false, null, false);
    }

    @Test
    void create_savesItemAndPublishesEvent() {
        when(repo.findByCompanyAndCode(COMPANY_ID, "SKU1")).thenReturn(Optional.empty());
        when(repo.save(any(Item.class))).thenAnswer(inv -> {
            Item i = inv.getArgument(0);
            i.setId(1L);
            return i;
        });

        ItemResponseDto result = service.create(
            new CreateItemRequestDto("SKU1", "Sugar 1kg", ItemType.SELLABLE, 10L, 20L, 30L));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.code()).isEqualTo("SKU1");
        verify(events).publish(eq("ItemCreated.v1"), any(), any(), any());
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(repo.findByCompanyAndCode(COMPANY_ID, "SKU1")).thenReturn(Optional.of(item(1L, ItemStatus.ACTIVE)));

        assertThatThrownBy(() -> service.create(
            new CreateItemRequestDto("SKU1", "Sugar", ItemType.SELLABLE, 10L, 20L, 30L)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(repo, never()).save(any());
    }

    @Test
    void listItems_withoutStatusFilter_delegatesToCompanyQuery() {
        var pageable = PageRequest.of(0, 20);
        when(repo.findByCompanyId(COMPANY_ID, pageable))
            .thenReturn(new PageImpl<>(List.of(item(1L, ItemStatus.ACTIVE), item(2L, ItemStatus.ACTIVE))));

        PageDto<ItemResponseDto> result = service.listItems(null, pageable);

        assertThat(result.content()).hasSize(2);
        assertThat(result.totalElements()).isEqualTo(2);
    }

    @Test
    void listItems_withStatusFilter_delegatesToStatusQuery() {
        var pageable = PageRequest.of(0, 20);
        when(repo.findByCompanyIdAndStatus(COMPANY_ID, ItemStatus.ARCHIVED, pageable))
            .thenReturn(new PageImpl<>(List.of(item(3L, ItemStatus.ARCHIVED))));

        PageDto<ItemResponseDto> result = service.listItems(ItemStatus.ARCHIVED, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).status()).isEqualTo(ItemStatus.ARCHIVED);
    }

    @Test
    void getItem_fromAnotherCompany_throwsNotFound() {
        Item foreign = new Item(999L, "SKU9", "Foreign", ItemType.SELLABLE, 1L, 1L, 1L, ACTOR_ID);
        foreign.setId(9L);
        when(repo.findById(9L)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getItem(9L)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void updateItem_appliesChangesAndPublishesEvent() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        ItemResponseDto result = service.updateItem(1L, plainUpdate("Sugar 2kg"));

        assertThat(result.name()).isEqualTo("Sugar 2kg");
        assertThat(existing.getType()).isEqualTo(ItemType.BOTH);
        assertThat(existing.getItemGroupId()).isEqualTo(11L);
        assertThat(existing.isTracked()).isFalse();
        verify(events).publish(eq("ItemUpdated.v1"), any(), any(), any());
    }

    @Test
    void updateItem_weighedWithoutUnit_isRejected() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        UpdateItemRequestDto request = new UpdateItemRequestDto("Bananas", null, ItemType.SELLABLE,
            11L, 21L, 31L, true, null, true, null, false);

        assertThatThrownBy(() -> service.updateItem(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("weighingUnit");
    }

    @Test
    void updateItem_weighedWithoutCapableBarcode_isRejected() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(barcodes.findByItemId(1L)).thenReturn(List.of(
            new ItemBarcode(1L, "5901234123457", BarcodeType.EAN13, null, BigDecimal.ONE)));

        UpdateItemRequestDto request = new UpdateItemRequestDto("Bananas", null, ItemType.SELLABLE,
            11L, 21L, 31L, true, null, true, WeighingUnit.KG, false);

        assertThatThrownBy(() -> service.updateItem(1L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("PLU or EMBEDDED_WEIGHT");
    }

    @Test
    void updateItem_weighedWithPluBarcode_succeedsAndEmitsWeighingEvent() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(barcodes.findByItemId(1L)).thenReturn(List.of(
            new ItemBarcode(1L, "21234", BarcodeType.PLU, null, BigDecimal.ONE)));

        UpdateItemRequestDto request = new UpdateItemRequestDto("Bananas", null, ItemType.SELLABLE,
            11L, 21L, 31L, true, null, true, WeighingUnit.KG, false);

        ItemResponseDto result = service.updateItem(1L, request);

        assertThat(result.weighed()).isTrue();
        assertThat(result.weighingUnit()).isEqualTo(WeighingUnit.KG);
        assertThat(existing.getWeighingUnit()).isEqualTo(WeighingUnit.KG);
        verify(events).publish(eq("ItemWeighingChanged.v1"), any(), any(), any());
    }

    @Test
    void updateItem_enablingBatchTracking_emitsEnabledEvent() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        UpdateItemRequestDto request = new UpdateItemRequestDto("Milk", null, ItemType.SELLABLE,
            11L, 21L, 31L, true, null, false, null, true);

        ItemResponseDto result = service.updateItem(1L, request);

        assertThat(result.batchTracked()).isTrue();
        verify(events).publish(eq("ItemBatchTrackingEnabled.v1"), any(), any(), any());
    }

    @Test
    void updateItem_disablingBatchTracking_emitsDisabledEvent() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        existing.setBatchTracked(true);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        UpdateItemRequestDto request = new UpdateItemRequestDto("Milk", null, ItemType.SELLABLE,
            11L, 21L, 31L, true, null, false, null, false);

        ItemResponseDto result = service.updateItem(1L, request);

        assertThat(result.batchTracked()).isFalse();
        verify(events).publish(eq("ItemBatchTrackingDisabled.v1"), any(), any(), any());
    }

    @Test
    void archiveItem_setsArchivedStatus() {
        Item existing = item(1L, ItemStatus.ACTIVE);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        service.archiveItem(1L);

        assertThat(existing.getStatus()).isEqualTo(ItemStatus.ARCHIVED);
        verify(events).publish(eq("ItemArchived.v1"), any(), any(), any());
    }

    @Test
    void archiveItem_rejectsAlreadyArchived() {
        when(repo.findById(1L)).thenReturn(Optional.of(item(1L, ItemStatus.ARCHIVED)));

        assertThatThrownBy(() -> service.archiveItem(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already archived");
    }

    @Test
    void activateItem_restoresActiveStatus() {
        Item existing = item(1L, ItemStatus.ARCHIVED);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));

        service.activateItem(1L);

        assertThat(existing.getStatus()).isEqualTo(ItemStatus.ACTIVE);
        verify(events).publish(eq("ItemActivated.v1"), any(), any(), any());
    }

    @Test
    void activateItem_rejectsAlreadyActive() {
        when(repo.findById(1L)).thenReturn(Optional.of(item(1L, ItemStatus.ACTIVE)));

        assertThatThrownBy(() -> service.activateItem(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already active");
    }
}
