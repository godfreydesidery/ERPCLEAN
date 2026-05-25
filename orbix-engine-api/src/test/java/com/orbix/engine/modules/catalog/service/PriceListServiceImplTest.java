package com.orbix.engine.modules.catalog.service;

import com.orbix.engine.modules.catalog.domain.dto.AdjustPricesRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.CopyPricesRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.CreatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.DiscontinuePriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListDto;
import com.orbix.engine.modules.catalog.domain.dto.PriceListItemDto;
import com.orbix.engine.modules.catalog.domain.dto.SetPriceRequestDto;
import com.orbix.engine.modules.catalog.domain.dto.UpdatePriceListRequestDto;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.PriceChangeLog;
import com.orbix.engine.modules.catalog.domain.entity.PriceList;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.entity.Uom;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.domain.enums.UomDimension;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceChangeLogRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListRepository;
import com.orbix.engine.modules.catalog.repository.UomRepository;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

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
    private static final Long APPROVER_ID = 77L;

    @Mock private PriceListRepository priceLists;
    @Mock private PriceListItemRepository priceListItems;
    @Mock private PriceChangeLogRepository priceChangeLog;
    @Mock private ItemRepository items;
    @Mock private UomRepository uoms;
    @Mock private EventPublisher events;
    @Mock private RequestContext context;
    @Mock private SettingsService settings;
    @Mock private PermissionResolverService permissions;

    @InjectMocks private PriceListServiceImpl service;

    @BeforeEach
    void bindContext() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        // approval gate disabled by default
        lenient().when(settings.getDecimal(SettingKey.PRICING_CHANGE_APPROVAL_PCT)).thenReturn(BigDecimal.ZERO);
    }

    private static PriceList priceList(Long id, String code, boolean isDefault) {
        PriceList list = new PriceList(COMPANY_ID, code, "Name " + code, "TZS",
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

    private static Uom uom(Long id, String code) {
        Uom uom = new Uom(code, "Unit " + code, UomDimension.COUNT, true, ACTOR_ID);
        uom.setId(id);
        ReflectionTestUtils.setField(uom, "uid", UidGenerator.next());
        return uom;
    }

    private static PriceListItem row(Long id, Long listId, Long itemId, Long uomId,
                                     BigDecimal minQty, String price, LocalDate from, LocalDate to) {
        PriceListItem r = new PriceListItem(listId, itemId, uomId, minQty, new BigDecimal(price), from);
        r.setId(id);
        r.setValidTo(to);
        return r;
    }

    // ---- price lists -------------------------------------------------------

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
            " retail ", "Retail", "TZS", LocalDate.of(2026, 1, 1), null, false, true));

        assertThat(result.code()).isEqualTo("RETAIL");
        assertThat(result.taxInclusive()).isTrue();
        assertThat(result.uid()).isNotBlank();
    }

    @Test
    void createPriceList_rejectsDuplicateCode() {
        when(priceLists.existsByCompanyIdAndCode(COMPANY_ID, "RETAIL")).thenReturn(true);

        assertThatThrownBy(() -> service.createPriceList(new CreatePriceListRequestDto(
            "RETAIL", "Retail", "TZS", LocalDate.of(2026, 1, 1), null, false, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
        verify(priceLists, never()).save(any());
    }

    @Test
    void createPriceList_rejectsValidToBeforeValidFrom() {
        when(priceLists.existsByCompanyIdAndCode(COMPANY_ID, "RETAIL")).thenReturn(false);

        assertThatThrownBy(() -> service.createPriceList(new CreatePriceListRequestDto(
            "RETAIL", "Retail", "TZS", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 1, 1), false, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("valid_to");
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
            "RETAIL", "Retail", "TZS", LocalDate.of(2026, 1, 1), null, true, false));

        assertThat(previous.isDefault()).isFalse();
    }

    @Test
    void getPriceListByCode_returnsMatch() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByCompanyIdAndCode(COMPANY_ID, "RETAIL")).thenReturn(Optional.of(list));

        assertThat(service.getPriceListByCode(" retail ").code()).isEqualTo("RETAIL");
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
    void updatePriceList_currencyChangeWithExistingPrices_isRejected() {
        PriceList existing = priceList(1L, "RETAIL", false);          // currency TZS
        when(priceLists.findByUid(existing.getUid())).thenReturn(Optional.of(existing));
        when(priceListItems.existsByPriceListId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updatePriceListByUid(existing.getUid(), new UpdatePriceListRequestDto(
            "Retail", "USD", LocalDate.of(2026, 1, 1), null, false, false)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
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

    // ---- set price ---------------------------------------------------------

    @Test
    void setPrice_firstSet_createsRowAndLogsWithNullOldPrice() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.empty());
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem r = inv.getArgument(0);
            r.setId(100L);
            return r;
        });

        PriceListItemDto result = service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1000"), LocalDate.of(2026, 1, 1), "initial", null));

        assertThat(result.price()).isEqualByComparingTo("1000");
        assertThat(result.itemCode()).isEqualTo("SKU8801");
        assertThat(result.uomCode()).isEqualTo("PCS");
        ArgumentCaptor<PriceChangeLog> log = ArgumentCaptor.forClass(PriceChangeLog.class);
        verify(priceChangeLog).save(log.capture());
        assertThat(log.getValue().getOldPrice()).isNull();
        assertThat(log.getValue().getNewPrice()).isEqualByComparingTo("1000");
        verify(events).publish(eq("ItemPriceChanged.v1"), any(), any(), any());
    }

    @Test
    void setPrice_replacingPrior_closesPriorRowAndLogsOldPrice() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = row(50L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(prior));
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem r = inv.getArgument(0);
            r.setId(101L);
            return r;
        });

        service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1200"), LocalDate.of(2026, 6, 1), "increase", null));

        assertThat(prior.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        ArgumentCaptor<PriceChangeLog> log = ArgumentCaptor.forClass(PriceChangeLog.class);
        verify(priceChangeLog).save(log.capture());
        assertThat(log.getValue().getOldPrice()).isEqualByComparingTo("1000");
        assertThat(log.getValue().getNewPrice()).isEqualByComparingTo("1200");
    }

    @Test
    void setPrice_tieredPrice_persistsMinQty() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(new BigDecimal("10")))).thenReturn(Optional.empty());
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem r = inv.getArgument(0);
            r.setId(102L);
            return r;
        });

        PriceListItemDto result = service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, new BigDecimal("10"), new BigDecimal("900"), LocalDate.of(2026, 1, 1), "bulk tier", null));

        assertThat(result.minQty()).isEqualByComparingTo("10");
        ArgumentCaptor<PriceListItem> saved = ArgumentCaptor.forClass(PriceListItem.class);
        verify(priceListItems).save(saved.capture());
        assertThat(saved.getValue().getMinQty()).isEqualByComparingTo("10");
    }

    @Test
    void setPrice_effectiveFromNotAfterPriorStart_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = row(50L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 6, 1), null);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1200"), LocalDate.of(2026, 3, 1), null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("take effect after");
        verify(priceListItems, never()).save(any());
    }

    @Test
    void setPrice_outsideListWindow_isRejected() {
        PriceList list = new PriceList(COMPANY_ID, "RETAIL", "Retail", "TZS",
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), false, false, ACTOR_ID);
        list.setId(1L);
        ReflectionTestUtils.setField(list, "uid", UidGenerator.next());
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1000"), LocalDate.of(2026, 6, 1), null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("validity window");
        verify(priceListItems, never()).save(any());
    }

    @Test
    void setPrice_onArchivedList_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        list.setStatus(ItemStatus.ARCHIVED);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1000"), LocalDate.of(2026, 1, 1), null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ARCHIVED");
    }

    @Test
    void setPrice_itemFromAnotherCompany_throwsNotFound() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, 999L)));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1000"), LocalDate.of(2026, 1, 1), null, null)))
            .isInstanceOf(NoSuchElementException.class);
    }

    // ---- approval gate -----------------------------------------------------

    @Test
    void setPrice_aboveThreshold_withoutApprover_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = row(50L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(settings.getDecimal(SettingKey.PRICING_CHANGE_APPROVAL_PCT)).thenReturn(new BigDecimal("10"));
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1500"), LocalDate.of(2026, 6, 1), "big jump", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("authoriser");
        verify(priceListItems, never()).save(any());
    }

    @Test
    void setPrice_aboveThreshold_selfApproval_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = row(50L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(settings.getDecimal(SettingKey.PRICING_CHANGE_APPROVAL_PCT)).thenReturn(new BigDecimal("10"));
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(prior));

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1500"), LocalDate.of(2026, 6, 1), "x", ACTOR_ID)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("your own");
    }

    @Test
    void setPrice_aboveThreshold_approverLacksPermission_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = row(50L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(settings.getDecimal(SettingKey.PRICING_CHANGE_APPROVAL_PCT)).thenReturn(new BigDecimal("10"));
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(prior));
        when(permissions.resolve(APPROVER_ID, COMPANY_ID, null)).thenReturn(Set.of());

        assertThatThrownBy(() -> service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1500"), LocalDate.of(2026, 6, 1), "x", APPROVER_ID)))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void setPrice_aboveThreshold_validApprover_succeeds() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem prior = row(50L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(settings.getDecimal(SettingKey.PRICING_CHANGE_APPROVAL_PCT)).thenReturn(new BigDecimal("10"));
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(prior));
        when(permissions.resolve(APPROVER_ID, COMPANY_ID, null)).thenReturn(Set.of("PRICE.APPROVE"));
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem r = inv.getArgument(0);
            r.setId(103L);
            return r;
        });

        PriceListItemDto result = service.setPriceByPriceListUid(list.getUid(), new SetPriceRequestDto(
            8801L, 2L, null, new BigDecimal("1500"), LocalDate.of(2026, 6, 1), "approved jump", APPROVER_ID));

        assertThat(result.price()).isEqualByComparingTo("1500");
        verify(priceListItems).save(any(PriceListItem.class));
    }

    // ---- discontinue -------------------------------------------------------

    @Test
    void discontinue_closesOpenRowAndLogsNullNewPrice() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem open = row(60L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(open));

        service.discontinuePriceByPriceListUid(list.getUid(), new DiscontinuePriceRequestDto(
            8801L, 2L, null, LocalDate.of(2026, 6, 1), "delisted"));

        assertThat(open.getValidTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        ArgumentCaptor<PriceChangeLog> log = ArgumentCaptor.forClass(PriceChangeLog.class);
        verify(priceChangeLog).save(log.capture());
        assertThat(log.getValue().getOldPrice()).isEqualByComparingTo("1000");
        assertThat(log.getValue().getNewPrice()).isNull();
        verify(events).publish(eq("ItemPriceDiscontinued.v1"), any(), any(), any());
    }

    @Test
    void discontinue_noActivePrice_isRejected() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.discontinuePriceByPriceListUid(list.getUid(),
            new DiscontinuePriceRequestDto(8801L, 2L, null, LocalDate.of(2026, 6, 1), null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No active price");
    }

    // ---- resolve -----------------------------------------------------------

    @Test
    void resolvePrice_picksBestQualifyingTier() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem tier10 = row(70L, 1L, 8801L, 2L, new BigDecimal("10"), "900", LocalDate.of(2026, 1, 1), null);
        PriceListItem tier0 = row(71L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(priceListItems.findEffectiveTiers(eq(1L), eq(8801L), eq(2L), any(BigDecimal.class), any(LocalDate.class)))
            .thenReturn(List.of(tier10, tier0));   // service takes the head (highest min_qty)
        when(items.findById(8801L)).thenReturn(Optional.of(item(8801L, COMPANY_ID)));
        when(uoms.findById(2L)).thenReturn(Optional.of(uom(2L, "PCS")));

        PriceListItemDto result = service.resolvePrice(list.getUid(), 8801L, 2L, new BigDecimal("15"), null);

        assertThat(result.price()).isEqualByComparingTo("900");
        assertThat(result.minQty()).isEqualByComparingTo("10");
    }

    @Test
    void resolvePrice_listNotEffective_throws() {
        PriceList list = priceList(1L, "RETAIL", false);
        list.setStatus(ItemStatus.ARCHIVED);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));

        assertThatThrownBy(() -> service.resolvePrice(list.getUid(), 8801L, 2L, null, null))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("not effective");
    }

    @Test
    void resolvePrice_noEffectivePrice_throws() {
        PriceList list = priceList(1L, "RETAIL", false);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(priceListItems.findEffectiveTiers(eq(1L), eq(8801L), eq(2L), any(BigDecimal.class), any(LocalDate.class)))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.resolvePrice(list.getUid(), 8801L, 2L, null, null))
            .isInstanceOf(NoSuchElementException.class)
            .hasMessageContaining("No effective price");
    }

    // ---- list (effective as-of) -------------------------------------------

    @Test
    void listPrices_returnsEnrichedEffectiveRows() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem r = row(80L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(priceListItems.findEffective(eq(1L), any(LocalDate.class))).thenReturn(List.of(r));
        when(items.findAllById(any())).thenReturn(List.of(item(8801L, COMPANY_ID)));
        when(uoms.findAllById(any())).thenReturn(List.of(uom(2L, "PCS")));

        List<PriceListItemDto> result = service.listPricesByPriceListUid(list.getUid(), null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).itemCode()).isEqualTo("SKU8801");
        assertThat(result.get(0).uomCode()).isEqualTo("PCS");
    }

    // ---- bulk --------------------------------------------------------------

    @Test
    void adjustPrices_shiftsEveryEffectiveRow() {
        PriceList list = priceList(1L, "RETAIL", false);
        PriceListItem r = row(90L, 1L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(priceLists.findByUid(list.getUid())).thenReturn(Optional.of(list));
        when(priceListItems.findEffective(eq(1L), any(LocalDate.class))).thenReturn(List.of(r));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.of(r));
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem s = inv.getArgument(0);
            s.setId(91L);
            return s;
        });

        int written = service.adjustPricesByPriceListUid(list.getUid(), new AdjustPricesRequestDto(
            new BigDecimal("10"), LocalDate.of(2026, 6, 1), "annual uplift", null));

        assertThat(written).isEqualTo(1);
        ArgumentCaptor<PriceListItem> saved = ArgumentCaptor.forClass(PriceListItem.class);
        verify(priceListItems).save(saved.capture());
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("1100");
    }

    @Test
    void copyPrices_copiesSourceEffectiveRowsWithAdjustment() {
        PriceList target = priceList(1L, "WHOLESALE", false);
        PriceList source = priceList(2L, "RETAIL", false);
        PriceListItem r = row(95L, 2L, 8801L, 2L, BigDecimal.ZERO, "1000", LocalDate.of(2026, 1, 1), null);
        when(priceLists.findByUid(target.getUid())).thenReturn(Optional.of(target));
        when(priceLists.findByUid(source.getUid())).thenReturn(Optional.of(source));
        when(priceListItems.findEffective(eq(2L), any(LocalDate.class))).thenReturn(List.of(r));
        when(priceListItems.findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
            eq(1L), eq(8801L), eq(2L), eq(BigDecimal.ZERO))).thenReturn(Optional.empty());
        when(priceListItems.save(any(PriceListItem.class))).thenAnswer(inv -> {
            PriceListItem s = inv.getArgument(0);
            s.setId(96L);
            return s;
        });

        int written = service.copyPricesIntoPriceListUid(target.getUid(), new CopyPricesRequestDto(
            source.getUid(), new BigDecimal("-10"), LocalDate.of(2026, 6, 1), "seed wholesale", null));

        assertThat(written).isEqualTo(1);
        ArgumentCaptor<PriceListItem> saved = ArgumentCaptor.forClass(PriceListItem.class);
        verify(priceListItems).save(saved.capture());
        assertThat(saved.getValue().getPriceListId()).isEqualTo(1L);
        assertThat(saved.getValue().getPrice()).isEqualByComparingTo("900");
    }

    @Test
    void copyPrices_currencyMismatch_isRejected() {
        PriceList target = priceList(1L, "WHOLESALE", false);
        PriceList source = new PriceList(COMPANY_ID, "USD_LIST", "USD list", "USD",
            LocalDate.of(2026, 1, 1), null, false, false, ACTOR_ID);
        source.setId(2L);
        ReflectionTestUtils.setField(source, "uid", UidGenerator.next());
        when(priceLists.findByUid(target.getUid())).thenReturn(Optional.of(target));
        when(priceLists.findByUid(source.getUid())).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.copyPricesIntoPriceListUid(target.getUid(), new CopyPricesRequestDto(
            source.getUid(), null, LocalDate.of(2026, 6, 1), null, null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("currency");
    }

    // ---- tenancy -----------------------------------------------------------

    @Test
    void getPriceList_fromAnotherCompany_throwsNotFound() {
        PriceList foreign = new PriceList(999L, "X", "Foreign", "TZS",
            LocalDate.of(2026, 1, 1), null, false, false, ACTOR_ID);
        foreign.setId(7L);
        ReflectionTestUtils.setField(foreign, "uid", UidGenerator.next());
        when(priceLists.findByUid(foreign.getUid())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.getPriceListByUid(foreign.getUid()))
            .isInstanceOf(NoSuchElementException.class);
    }
}
