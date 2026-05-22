package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.PriceChangeLog;
import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceChangeLogRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListRepository;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceListServiceImplTest {

    private static final Long COMPANY_ID = 4L;
    private static final Long ACTOR_ID = 9L;

    @Mock private PriceListRepository priceLists;
    @Mock private PriceListItemRepository priceListItems;
    @Mock private PriceChangeLogRepository priceChangeLog;
    @Mock private ItemRepository items;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;

    @InjectMocks private PriceListServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private static PriceList priceList(Long id, String code, boolean isDefault) {
        PriceList list = new PriceList(COMPANY_ID, code, "Name " + code, "UGX",
            LocalDate.of(2026, 1, 1), null, isDefault, false, ACTOR_ID);
        list.setId(id);
        ReflectionTestUtils.setField(list, "uid", UidGenerator.next());
        return list;
    }

    private static Item item(Long id, Long companyId) {
        Item item = new Item(companyId, "SKU" + id, "Item " + id, ItemType.SELLABLE,
            1L, 1L, 1L, ACTOR_ID);
        item.setId(id);
        ReflectionTestUtils.setField(item, "uid", UidGenerator.next());
        return item;
    }

    @Test
    void createPriceList_uppercasesCode() {
        when(priceLists.existsByCompanyIdAndCode(COMPANY_ID, "RETAIL")).thenReturn(false);
        when(priceLists.save(any(PriceList.class))).thenAnswer(inv -> {
            PriceList l = inv.getArgument(0);
            l.setId(1L);
            ReflectionTestUtils.setField(l, "uid", UidGenerator.next());
            return l;
        });

        PriceListDto result = service.createPriceList(new CreatePriceListRequestDto(
            " retail ", "Retail", "UGX", LocalDate.of(2026, 1, 1), null, false, true));

        assertThat(result.code()).isEqualTo("RETAIL");
        assertThat(result.taxInclusive()).isTrue();
        assertThat(result.uid()).isNotBlank();
    }

    @Test
    void createPriceList_rejectsDuplicateCode() {
        when(priceLists.existsByCompanyIdAndCode(COMPANY_ID, "RETAIL")).thenReturn(true);

        assertThatThrownBy(() -> service.createPriceList(new CreatePriceListRequestDto(
            "RETAIL", "Retail", "UGX", LocalDate.of(2026, 1, 1), null, false, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(priceLists, never()).save(any());
    }

    @Test
    void createPriceList_asDefault_clearsPreviousDefault() {
        PriceList previous = priceList(5L, "OLD", true);
        when(priceLists.existsByCompanyIdAndCode(COMPANY_ID, "RETAIL")).thenReturn(false);
        when(priceLists.save(any(PriceList.class))).thenAnswer(inv -> {
            PriceList l = inv.getArgument(0);
            l.setId(1L);
            ReflectionTestUtils.setField(l, "uid", UidGenerator.next());
            return l;
        });
        when(priceLists.findByCompanyIdAndIsDefaultTrue(COMPANY_ID))
            .thenReturn(List.of(previous, priceList(1L, "RETAIL", true)));

        service.createPriceList(new CreatePriceListRequestDto(
            "RETAIL", "Retail", "UGX", LocalDate.of(2026, 1, 1), null, true, false));

        assertThat(previous.isDefault()).isFalse();
    }

    @Test
    void updatePriceList_changesAttributes() {
        PriceList existing = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        PriceListDto result = service.updatePriceListByUid(existing.getUid(), new UpdatePriceListRequestDto(
            "Retail 2026", "USD", LocalDate.of(2026, 7, 1), null, false, true));

        assertThat(result.name()).isEqualTo("Retail 2026");
        assertThat(existing.getCurrencyCode()).isEqualTo("USD");
        assertThat(existing.isTaxInclusive()).isTrue();
    }

    @Test
    void archivePriceList_rejectsAlreadyArchived() {
        PriceList existing = priceList(1L, "RETAIL", false);
        existing.setStatus(ItemStatus.ARCHIVED);
        when(priceLists.findByUid(existing.getUid())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.archivePriceListByUid(existing.getUid()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already archived");
    }

    @Test
    void setPrice_firstSet_createsRowAndLogsWithNullOldPrice() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(priceListItems.findByPriceListIdAndItemIdAndUomIdAndValidToIsNull(1L, 8801L, 2L))
            .thenReturn(Optional.empty());
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        PriceListItemDto result = service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, new BigDecimal("1000"), LocalDate.of(2026, 1, 1), "initial"));

        assertThat(result.price()).isEqualByComparingTo("1000");
        ArgumentCaptor<PriceChangeLog> log = ArgumentCaptor.forClass(PriceChangeLog.class);
        verify(priceChangeLog).save(log.capture());
        assertThat(log.getValue().getOldPrice()).isNull();
        assertThat(log.getValue().getNewPrice()).isEqualByComparingTo("1000");
        verify(events).publish(eq("ItemPriceChanged.v1"), any(), any(), any());
    }

    @Test
    void setPrice_replacingPrior_closesPriorRowAndLogsOldPrice() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = new PriceListItem(1L, 8801L, 2L, new BigDecimal("1000"), LocalDate.of(2026, 1, 1));
        prior.setId(50L);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(priceListItems.findByPriceListIdAndItemIdAndUomIdAndValidToIsNull(1L, 8801L, 2L))
            .thenReturn(Optional.of(prior));
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem r = inv.getArgument(0);
            r.setId(101L);
            return r;
        });

        service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, new BigDecimal("1200"), LocalDate.of(2026, 6, 1), "increase"));

        assertThat(prior.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        ArgumentCaptor<PriceChangeLog> log = ArgumentCaptor.forClass(PriceChangeLog.class);
        verify(priceChangeLog).save(log.capture());
        assertThat(log.getValue().getOldPrice()).isEqualByComparingTo("1000");
        assertThat(log.getValue().getNewPrice()).isEqualByComparingTo("1200");
    }

    @Test
    void setPrice_effectiveFromNotAfterPriorStart_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = new PriceListItem(1L, 8801L, 2L, new BigDecimal("1000"), LocalDate.of(2026, 6, 1));
        prior.setId(50L);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(priceListItems.findByPriceListIdAndItemIdAndUomIdAndValidToIsNull(1L, 8801L, 2L))
            .thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, new BigDecimal("1200"), LocalDate.of(2026, 1, 1), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("take effect after");
        verify(priceListItems, never()).save(any());
    }

    @Test
    void setPrice_itemFromAnotherCompany_throwsNotFound() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, 999L)));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, new BigDecimal("1000"), LocalDate.of(2026, 1, 1), null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getPriceList_fromAnotherCompany_throwsNotFound() {
        PriceList foreign = new PriceList(999L, "X", "Foreign", "UGX",
            LocalDate.of(2026, 1, 1), null, false, false, ACTOR_ID);
        foreign.setId(7L);
        ReflectionTestUtils.setField(foreign, "uid", UidGenerator.next());
        when(priceLists.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getPriceListByUid(foreign.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
