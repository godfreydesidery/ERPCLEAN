package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.BarcodeType;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.domain.enums.ItemType;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;
import com.orbix.engine.modules.pos.domain.enums.PosPaymentMethod;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;
    private static final Long PRICE_LIST_ID = 5L;
    private static final Long ITEM_A = 8801L;
    private static final Long ITEM_B = 8802L;
    private static final Long UOM_ID = 1L;
    private static final Long VAT_ID = 2L;
    private static final Long ACTOR_ID = 4L;

    @Mock private PosSaleService posSaleService;
    @Mock private ItemRepository items;
    @Mock private ItemBarcodeRepository barcodes;
    @Mock private VatGroupRepository vatGroups;
    @Mock private PriceListItemRepository priceListItems;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private RequestContext context;

    @InjectMocks private SyncServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
    }

    private PostPosSaleRequestDto sale(String clientOpId) {
        return new PostPosSaleRequestDto(
            "TILL-1-" + clientOpId, clientOpId, 200L, 33L, 540L, null, null,
            Instant.parse("2026-05-13T10:00:00Z"), null,
            List.of(new PostPosSaleRequestDto.Line(ITEM_A, UOM_ID,
                new BigDecimal("1"), new BigDecimal("100"), null, VAT_ID)),
            List.of(new PostPosSaleRequestDto.Payment(PosPaymentMethod.CASH,
                new BigDecimal("118"), null, null, null, null)),
            null
        );
    }

    @Test
    void pushBatch_allAccepted_returnsAcceptedItems() {
        when(posSaleService.post(any())).thenAnswer(inv -> {
            PostPosSaleRequestDto req = inv.getArgument(0);
            return stubPostedDto(req.clientOpId(), 9001L + req.clientOpId().hashCode());
        });

        SyncPushResultDto result = service.pushBatch(new SyncPushRequestDto(
            List.of(sale("op-a"), sale("op-b"), sale("op-c"))));

        assertThat(result.acceptedCount()).isEqualTo(3);
        assertThat(result.rejectedCount()).isZero();
        assertThat(result.items()).extracting(SyncPushResultDto.Item::accepted)
            .containsExactly(true, true, true);
        verify(posSaleService, times(3)).post(any());
    }

    @Test
    void pushBatch_partialFailure_isolatesPerItem() {
        // Item 2 fails; the others succeed and are accepted.
        when(posSaleService.post(any())).thenAnswer(inv -> {
            PostPosSaleRequestDto req = inv.getArgument(0);
            if ("op-bad".equals(req.clientOpId())) {
                throw new IllegalArgumentException("Tender sum below total");
            }
            return stubPostedDto(req.clientOpId(), 9100L + req.clientOpId().hashCode());
        });

        SyncPushResultDto result = service.pushBatch(new SyncPushRequestDto(
            List.of(sale("op-good-1"), sale("op-bad"), sale("op-good-2"))));

        assertThat(result.acceptedCount()).isEqualTo(2);
        assertThat(result.rejectedCount()).isEqualTo(1);
        SyncPushResultDto.Item rejected = result.items().get(1);
        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.clientOpId()).isEqualTo("op-bad");
        assertThat(rejected.errorMessage()).contains("below total");
    }

    @Test
    void catalogSnapshot_returnsItemsWithPriceAndOnHand() {
        Item a = new Item(COMPANY_ID, "SKU-A", "Sugar", ItemType.SELLABLE, 10L, UOM_ID, VAT_ID, ACTOR_ID);
        a.setId(ITEM_A);
        a.setMinSellPrice(new BigDecimal("45"));
        Item b = new Item(COMPANY_ID, "SKU-B", "Salt", ItemType.SELLABLE, 10L, UOM_ID, VAT_ID, ACTOR_ID);
        b.setId(ITEM_B);
        when(items.findByCompanyIdAndStatusOrderByIdAsc(COMPANY_ID, ItemStatus.ACTIVE))
            .thenReturn(List.of(a, b));

        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        vat.setId(VAT_ID);
        when(vatGroups.findAll()).thenReturn(List.of(vat));

        PriceListItem priceA = new PriceListItem(PRICE_LIST_ID, ITEM_A, UOM_ID, new BigDecimal("100"),
            LocalDate.of(2026, 1, 1));
        priceA.setId(70L);
        when(priceListItems.findByPriceListIdAndValidToIsNull(PRICE_LIST_ID))
            .thenReturn(List.of(priceA));

        ItemBranchBalance bal = new ItemBranchBalance(ITEM_A, BRANCH_ID);
        bal.setQtyOnHand(new BigDecimal("50"));
        when(balances.findByBranchId(BRANCH_ID)).thenReturn(List.of(bal));

        when(barcodes.findByItemId(ITEM_A)).thenReturn(List.of(
            new ItemBarcode(ITEM_A, "1234567890123", BarcodeType.EAN13, null, BigDecimal.ONE)));
        when(barcodes.findByItemId(ITEM_B)).thenReturn(List.of());

        CatalogSnapshotDto dto = service.catalogSnapshot(BRANCH_ID, PRICE_LIST_ID);

        assertThat(dto.branchId()).isEqualTo(BRANCH_ID);
        assertThat(dto.priceListId()).isEqualTo(PRICE_LIST_ID);
        assertThat(dto.items()).hasSize(2);
        var snapA = dto.items().get(0);
        assertThat(snapA.itemId()).isEqualTo(ITEM_A);
        assertThat(snapA.code()).isEqualTo("SKU-A");
        assertThat(snapA.vatRate()).isEqualByComparingTo("0.18");
        assertThat(snapA.price()).isEqualByComparingTo("100");
        assertThat(snapA.qtyOnHand()).isEqualByComparingTo("50");
        assertThat(snapA.minSellPrice()).isEqualByComparingTo("45");
        assertThat(snapA.barcodes()).hasSize(1);
        assertThat(snapA.barcodes().get(0).barcode()).isEqualTo("1234567890123");

        var snapB = dto.items().get(1);
        assertThat(snapB.itemId()).isEqualTo(ITEM_B);
        assertThat(snapB.price()).isEqualByComparingTo("0");      // no price-list row
        assertThat(snapB.qtyOnHand()).isEqualByComparingTo("0");  // no balance row
    }

    @Test
    void balanceSnapshot_returnsAllBranchRows() {
        ItemBranchBalance b1 = new ItemBranchBalance(ITEM_A, BRANCH_ID);
        b1.setQtyOnHand(new BigDecimal("50"));
        ItemBranchBalance b2 = new ItemBranchBalance(ITEM_B, BRANCH_ID);
        b2.setQtyOnHand(new BigDecimal("12.5"));
        when(balances.findByBranchId(BRANCH_ID)).thenReturn(List.of(b1, b2));

        BalanceSnapshotDto dto = service.balanceSnapshot(BRANCH_ID);

        assertThat(dto.branchId()).isEqualTo(BRANCH_ID);
        assertThat(dto.balances()).hasSize(2);
        assertThat(dto.balances().get(0).qtyOnHand()).isEqualByComparingTo("50");
        assertThat(dto.balances().get(1).qtyOnHand()).isEqualByComparingTo("12.5");
    }

    private PosSaleDto stubPostedDto(String clientOpId, long id) {
        return new PosSaleDto(
            id, "TILL-1-" + clientOpId, clientOpId, 200L, 100L, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            com.orbix.engine.modules.pos.domain.enums.PosSaleKind.SALE, null,
            Instant.parse("2026-05-13T10:00:00Z"), Instant.now(), LocalDate.of(2026, 5, 13),
            new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("18"), new BigDecimal("118"),
            new BigDecimal("118"), BigDecimal.ZERO,
            com.orbix.engine.modules.pos.domain.enums.PosSaleStatus.POSTED,
            null, null, null, null, List.of(), List.of()
        );
    }
}
