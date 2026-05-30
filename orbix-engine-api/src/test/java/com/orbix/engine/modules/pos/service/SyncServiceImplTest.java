package com.orbix.engine.modules.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.orbix.engine.modules.catalog.repository.PriceListRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.util.UidGenerator;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyCategory;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.pos.domain.dto.BalanceSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.CatalogSnapshotDto;
import com.orbix.engine.modules.pos.domain.dto.PosSaleDto;
import com.orbix.engine.modules.pos.domain.dto.PostPosSaleRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncCursorDto;
import com.orbix.engine.modules.pos.domain.dto.SyncOpDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPullResultDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushRequestDto;
import com.orbix.engine.modules.pos.domain.dto.SyncPushResultDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionCloseRequestDto;
import com.orbix.engine.modules.pos.domain.dto.TillSessionCloseResultDto;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SyncServiceImpl} — covers the release-critical
 * scenarios called out in docs/design/slice-sync-spine.md §7.1:
 * <ol>
 *   <li>Idempotent double-push → DUPLICATE (no second row)</li>
 *   <li>dependsOn DEFERRED → applies next cycle once parent is settled</li>
 *   <li>Cursor monotonicity + paging (hasMore, nextCursor advances)</li>
 *   <li>Reconciliation: manifest match → CLOSED; mismatch → RECONCILE_INCOMPLETE</li>
 *   <li>Tenancy isolation — company_id from RequestContext, never payload</li>
 *   <li>Partial batch failure — one REJECTED does not roll back siblings</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SyncServiceImplTest {

    private static final Long COMPANY_ID  = 7L;
    private static final Long BRANCH_ID   = 12L;
    private static final Long PRICE_LIST_ID = 5L;
    private static final Long ITEM_A      = 8801L;
    private static final Long ITEM_B      = 8802L;
    private static final Long UOM_ID      = 1L;
    private static final Long VAT_ID      = 2L;
    private static final Long ACTOR_ID    = 4L;
    private static final Long SESSION_ID  = 99L;

    @Mock private PosSaleService        posSaleService;
    @Mock private TillSessionService    tillSessionService;
    @Mock private TillSessionRepository sessions;
    @Mock private CashPickupRepository  pickups;
    @Mock private PettyCashRepository   pettyCash;
    @Mock private PosSaleRepository     sales;
    @Mock private ItemRepository        items;
    @Mock private ItemBarcodeRepository barcodes;
    @Mock private VatGroupRepository    vatGroups;
    @Mock private PriceListItemRepository priceListItems;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private PriceListRepository   priceListRepo;
    @Mock private PartyRepository       partyRepository;
    @Mock private CustomerRepository    customerRepository;
    @Mock private RequestContext        context;

    @InjectMocks private SyncServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.requireBranchId()).thenReturn(BRANCH_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        // Wire ObjectMapper (not injectable via @Mock — set via reflection)
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper()
            .findAndRegisterModules());
        ReflectionTestUtils.setField(service, "pushBatchMax", 500);
        ReflectionTestUtils.setField(service, "pullPageSize", 3); // small cap for paging tests
    }

    // -----------------------------------------------------------------------
    // Push — POS_SALE happy path
    // -----------------------------------------------------------------------

    @Test
    void push_posSale_acceptedOnFirstPost() {
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-a")).thenReturn(Optional.empty());
        when(posSaleService.post(any())).thenReturn(stubSaleDto("op-a", 9001L));

        SyncPushResultDto result = service.pushBatch(pushRequest("op-a", posSalePayload("op-a")));

        assertThat(result.batchAcceptedCount()).isEqualTo(1);
        assertThat(result.batchRejectedCount()).isZero();
        assertThat(result.results()).hasSize(1);
        SyncPushResultDto.OpResultDto r = result.results().get(0);
        assertThat(r.verdict()).isEqualTo("ACCEPTED");
        assertThat(r.clientOpId()).isEqualTo("op-a");
        assertThat(r.serverEntityId()).isEqualTo("9001");
        assertThat(r.errorCode()).isNull();
        assertThat(result.serverReceivedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // US-POS-018 §7.1.2 — idempotent double-push → DUPLICATE, no second row
    // -----------------------------------------------------------------------

    @Test
    void push_posSale_duplicateClientOpId_returnsDuplicateVerdictNotSecondRow() {
        // Server already holds this clientOpId (sequential retry path).
        PosSale existing = stubSaleEntity("op-dup", 9002L);
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-dup"))
            .thenReturn(Optional.of(existing));

        SyncPushResultDto result = service.pushBatch(pushRequest("op-dup", posSalePayload("op-dup")));

        SyncPushResultDto.OpResultDto r = result.results().get(0);
        assertThat(r.verdict()).isEqualTo("DUPLICATE");
        assertThat(r.serverEntityId()).isEqualTo("9002");
        // SYNC-003 fix: pre-check short-circuits — post() is never called on a known duplicate.
        verify(posSaleService, never()).post(any());
    }

    // -----------------------------------------------------------------------
    // Partial batch — one REJECTED does not roll back siblings (§2.4)
    // -----------------------------------------------------------------------

    @Test
    void push_partialBatch_rejectedOpDoesNotAffectSiblings() {
        when(sales.findByCompanyIdAndClientOpId(eq(COMPANY_ID), any())).thenReturn(Optional.empty());
        when(posSaleService.post(any())).thenAnswer(inv -> {
            PostPosSaleRequestDto req = inv.getArgument(0);
            if ("op-bad".equals(req.clientOpId())) {
                throw new IllegalArgumentException("Tender sum below total");
            }
            return stubSaleDto(req.clientOpId(), 9100L + req.clientOpId().hashCode());
        });

        SyncPushResultDto result = service.pushBatch(new SyncPushRequestDto(
            "TILL-1", 1,
            List.of(
                syncOp("op-good-1", "POS_SALE", null, posSalePayload("op-good-1")),
                syncOp("op-bad",    "POS_SALE", null, posSalePayload("op-bad")),
                syncOp("op-good-2", "POS_SALE", null, posSalePayload("op-good-2"))
            )
        ));

        assertThat(result.batchAcceptedCount()).isEqualTo(2);
        assertThat(result.batchRejectedCount()).isEqualTo(1);
        assertThat(result.results().get(0).verdict()).isEqualTo("ACCEPTED");
        assertThat(result.results().get(1).verdict()).isEqualTo("REJECTED");
        assertThat(result.results().get(1).errorMessage()).contains("below total");
        assertThat(result.results().get(2).verdict()).isEqualTo("ACCEPTED");
    }

    // -----------------------------------------------------------------------
    // dependsOn DEFERRED — op with unresolved parent stays DEFERRED (§2.4)
    // -----------------------------------------------------------------------

    @Test
    void push_dependsOn_deferredWhenParentNotSettled() {
        // op-child depends on op-parent which is NOT in this batch — no stubs needed because
        // the DEFERRED short-circuit fires before applyOp is called.
        SyncPushResultDto result = service.pushBatch(new SyncPushRequestDto(
            "TILL-1", 1,
            List.of(syncOp("op-child", "POS_SALE", "op-parent-missing", posSalePayload("op-child")))
        ));

        assertThat(result.results().get(0).verdict()).isEqualTo("DEFERRED");
        // DEFERRED is not counted as rejected
        assertThat(result.batchRejectedCount()).isZero();
        verify(posSaleService, never()).post(any());
    }

    @Test
    void push_dependsOn_appliedWhenParentAcceptedInSameBatch() {
        // op-parent lands first and is ACCEPTED; op-child depends on it and must also be ACCEPTED.
        when(sales.findByCompanyIdAndClientOpId(eq(COMPANY_ID), any())).thenReturn(Optional.empty());
        when(posSaleService.post(any())).thenAnswer(inv -> {
            PostPosSaleRequestDto req = inv.getArgument(0);
            return stubSaleDto(req.clientOpId(), 9300L + req.clientOpId().hashCode());
        });

        SyncPushResultDto result = service.pushBatch(new SyncPushRequestDto(
            "TILL-1", 1,
            List.of(
                syncOp("op-parent", "POS_SALE", null,         posSalePayload("op-parent")),
                syncOp("op-child",  "POS_SALE", "op-parent",  posSalePayload("op-child"))
            )
        ));

        assertThat(result.results().get(0).verdict()).isEqualTo("ACCEPTED");
        assertThat(result.results().get(1).verdict()).isEqualTo("ACCEPTED");
        assertThat(result.batchAcceptedCount()).isEqualTo(2);
    }

    // -----------------------------------------------------------------------
    // Batch size cap
    // -----------------------------------------------------------------------

    @Test
    void push_batchExceedsMax_throwsIllegalArgument() {
        ReflectionTestUtils.setField(service, "pushBatchMax", 2);
        SyncPushRequestDto oversized = new SyncPushRequestDto("TILL-1", 1, List.of(
            syncOp("op-1", "POS_SALE", null, posSalePayload("op-1")),
            syncOp("op-2", "POS_SALE", null, posSalePayload("op-2")),
            syncOp("op-3", "POS_SALE", null, posSalePayload("op-3"))
        ));
        assertThatThrownBy(() -> service.pushBatch(oversized))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("exceeds server maximum");
    }

    // -----------------------------------------------------------------------
    // FIELD_SALE out of scope — returns REJECTED with a clear error code
    // -----------------------------------------------------------------------

    @Test
    void push_fieldSale_rejectedWithNotSupportedCode() {
        SyncPushResultDto result = service.pushBatch(new SyncPushRequestDto(
            "WMS-1", 1,
            List.of(syncOp("op-fs", "FIELD_SALE", null, Map.of("something", "value")))
        ));
        assertThat(result.results().get(0).verdict()).isEqualTo("REJECTED");
        assertThat(result.results().get(0).errorCode()).isEqualTo("FIELD_SALE_NOT_SUPPORTED");
    }

    // -----------------------------------------------------------------------
    // Pull — cursor monotonicity and paging (§7.1.5)
    // -----------------------------------------------------------------------

    @Test
    void pull_fromZeroCursor_returnsItemsWithNextCursor() {
        Item a = activeItem(ITEM_A, "SKU-A", 10L);
        ReflectionTestUtils.setField(a, "changeSeq", 5L);
        Item b = activeItem(ITEM_B, "SKU-B", 20L);
        ReflectionTestUtils.setField(b, "changeSeq", 8L);

        when(items.findByCompanyIdAndChangeSeqGreaterThan(eq(COMPANY_ID), eq(0L), any()))
            .thenReturn(List.of(a, b));
        when(barcodes.findByItemId(any())).thenReturn(List.of());

        SyncPullResultDto result = service.pull(null, "catalog");

        assertThat(result.datasets()).containsKey("catalog");
        assertThat(result.datasets().get("catalog").upserts()).hasSize(2);
        assertThat(result.datasets().get("catalog").deletes()).isEmpty();
        assertThat(result.nextCursor()).isNotBlank();
        // Cursor must encode seq >= 8 (the max changeSeq seen)
        SyncCursorDto decoded = SyncCursorDto.decode(result.nextCursor());
        assertThat(decoded.seq()).isGreaterThanOrEqualTo(8L);
    }

    @Test
    void pull_cursorAdvances_onlyDeltaReturned() {
        // First pull seeds cursor at seq=5; second pull asks for seq>5 and gets only the new item.
        Item newItem = activeItem(ITEM_B, "SKU-B", 20L);
        ReflectionTestUtils.setField(newItem, "changeSeq", 12L);

        String cursor = new SyncCursorDto(1, 5L).encode();
        when(items.findByCompanyIdAndChangeSeqGreaterThan(eq(COMPANY_ID), eq(5L), any()))
            .thenReturn(List.of(newItem));
        when(barcodes.findByItemId(any())).thenReturn(List.of());

        SyncPullResultDto result = service.pull(cursor, "catalog");

        assertThat(result.datasets().get("catalog").upserts()).hasSize(1);
        SyncCursorDto next = SyncCursorDto.decode(result.nextCursor());
        assertThat(next.seq()).isGreaterThanOrEqualTo(12L);
    }

    @Test
    void pull_hasMoreTrue_whenPageFull() {
        // pullPageSize is set to 3 in @BeforeEach; return exactly 3 items → hasMore=true.
        List<Item> fullPage = List.of(
            seqItem(ITEM_A, 1L), seqItem(ITEM_B, 2L), seqItem(8803L, 3L)
        );
        when(items.findByCompanyIdAndChangeSeqGreaterThan(eq(COMPANY_ID), eq(0L), any()))
            .thenReturn(fullPage);
        when(barcodes.findByItemId(any())).thenReturn(List.of());

        SyncPullResultDto result = service.pull(null, "catalog");

        assertThat(result.hasMore()).isTrue();
    }

    @Test
    void pull_hasMoreFalse_whenPagePartial() {
        List<Item> partial = List.of(seqItem(ITEM_A, 1L));  // less than cap of 3
        when(items.findByCompanyIdAndChangeSeqGreaterThan(eq(COMPANY_ID), eq(0L), any()))
            .thenReturn(partial);
        when(barcodes.findByItemId(any())).thenReturn(List.of());

        SyncPullResultDto result = service.pull(null, "catalog");

        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void pull_archivedItem_surfacedAsDelete() {
        Item archived = activeItem(ITEM_A, "SKU-A", 10L);
        ReflectionTestUtils.setField(archived, "status", ItemStatus.ARCHIVED);
        ReflectionTestUtils.setField(archived, "changeSeq", 9L);

        when(items.findByCompanyIdAndChangeSeqGreaterThan(eq(COMPANY_ID), eq(0L), any()))
            .thenReturn(List.of(archived));

        SyncPullResultDto result = service.pull(null, "catalog");

        assertThat(result.datasets().get("catalog").upserts()).isEmpty();
        assertThat(result.datasets().get("catalog").deletes()).containsExactly(String.valueOf(ITEM_A));
    }

    // -----------------------------------------------------------------------
    // Tenancy isolation — company_id always from RequestContext (§7.1.4)
    // -----------------------------------------------------------------------

    @Test
    void push_tenancy_companyIdFromContextNotPayload() {
        // Even if the payload somehow embeds a different companyId, the
        // idempotency lookup and stamp always use context.companyId().
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-tenant")).thenReturn(Optional.empty());
        when(posSaleService.post(any())).thenReturn(stubSaleDto("op-tenant", 9500L));

        service.pushBatch(pushRequest("op-tenant", posSalePayload("op-tenant")));

        // Verify the lookup used COMPANY_ID, not any payload value.
        verify(sales).findByCompanyIdAndClientOpId(COMPANY_ID, "op-tenant");
    }

    // -----------------------------------------------------------------------
    // Reconciliation — closeTillSession (§7.1.6)
    // -----------------------------------------------------------------------

    @Test
    void closeTillSession_manifestMatch_returnsClosed() {
        TillSession session = openSession();
        String saleOpId = "op-sale-1";
        PosSale sale = stubSaleEntity(saleOpId, 555L);
        sale.setTillSessionId(SESSION_ID);

        when(sessions.findByCompanyIdAndClientOpId(COMPANY_ID, "op-session-open"))
            .thenReturn(Optional.of(session));
        when(sales.findByTillSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of(sale));
        when(pickups.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());
        when(pettyCash.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(pickups.sumForSession(SESSION_ID)).thenReturn(BigDecimal.ZERO);
        when(pettyCash.sumForSession(SESSION_ID)).thenReturn(BigDecimal.ZERO);

        TillSessionCloseRequestDto req = new TillSessionCloseRequestDto(
            "op-session-open",
            new BigDecimal("1500"),
            new TillSessionCloseRequestDto.ManifestDto(
                1, new BigDecimal("1000"), 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                List.of(saleOpId)
            )
        );

        TillSessionCloseResultDto result = service.closeTillSession(req);

        assertThat(result.status()).isEqualTo("CLOSED");
        assertThat(result.confirmedClientOpIds()).containsExactly(saleOpId);
        assertThat(result.missingClientOpIds()).isEmpty();
        assertThat(result.unexpectedClientOpIds()).isEmpty();
        assertThat(result.declaredCash()).isEqualByComparingTo("1500");
    }

    @Test
    void closeTillSession_manifestMismatch_returnsReconcileIncomplete() {
        TillSession session = openSession();
        // Server has op-sale-1 but client claims op-sale-2 (different op).
        PosSale sale = stubSaleEntity("op-sale-server", 556L);
        sale.setTillSessionId(SESSION_ID);

        when(sessions.findByCompanyIdAndClientOpId(COMPANY_ID, "op-session-open"))
            .thenReturn(Optional.of(session));
        when(sales.findByTillSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of(sale));
        when(pickups.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());
        when(pettyCash.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());

        TillSessionCloseRequestDto req = new TillSessionCloseRequestDto(
            "op-session-open",
            new BigDecimal("1500"),
            new TillSessionCloseRequestDto.ManifestDto(
                1, new BigDecimal("1000"), 0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                List.of("op-sale-client-claims")   // not what server has
            )
        );

        TillSessionCloseResultDto result = service.closeTillSession(req);

        assertThat(result.status()).isEqualTo("RECONCILE_INCOMPLETE");
        // "op-sale-client-claims" is in client's list but not on server → missing
        assertThat(result.missingClientOpIds()).containsExactly("op-sale-client-claims");
        // "op-sale-server" is on server but not in client's list → unexpected
        assertThat(result.unexpectedClientOpIds()).containsExactly("op-sale-server");
    }

    @Test
    void closeTillSession_alreadyClosed_idempotentReturnsClosed() {
        TillSession session = openSession();
        // Force-set status to CLOSED so the idempotent path triggers.
        ReflectionTestUtils.setField(session, "status", TillSessionStatus.CLOSED);
        ReflectionTestUtils.setField(session, "expectedCashAmount", new BigDecimal("1000"));
        ReflectionTestUtils.setField(session, "declaredCashAmount", new BigDecimal("1000"));
        ReflectionTestUtils.setField(session, "varianceAmount",     BigDecimal.ZERO);

        when(sessions.findByCompanyIdAndClientOpId(COMPANY_ID, "op-session-open"))
            .thenReturn(Optional.of(session));
        when(sales.findByTillSessionIdOrderByIdAsc(SESSION_ID)).thenReturn(List.of());
        when(pickups.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());
        when(pettyCash.findByTillSessionIdOrderByAtAsc(SESSION_ID)).thenReturn(List.of());

        TillSessionCloseResultDto result = service.closeTillSession(
            new TillSessionCloseRequestDto("op-session-open", new BigDecimal("1000"),
                new TillSessionCloseRequestDto.ManifestDto(0, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                    0, BigDecimal.ZERO, List.of())));

        assertThat(result.status()).isEqualTo("CLOSED");
        // session.close() must NOT be called again (entity would throw IllegalStateException)
        // — verified implicitly since no save is needed and no ISE is thrown.
    }

    // -----------------------------------------------------------------------
    // Legacy snapshot endpoints (backward-compat)
    // -----------------------------------------------------------------------

    @Test
    void catalogSnapshot_returnsItemsWithPriceAndOnHand() {
        Item a = activeItem(ITEM_A, "SKU-A", 10L);
        ReflectionTestUtils.setField(a, "minSellPrice", new BigDecimal("45"));
        Item b = activeItem(ITEM_B, "SKU-B", 10L);
        when(items.findByCompanyIdAndStatusOrderByIdAsc(COMPANY_ID, ItemStatus.ACTIVE))
            .thenReturn(List.of(a, b));

        VatGroup vat = new VatGroup(COMPANY_ID, "STD", "Standard", new BigDecimal("0.18"),
            LocalDate.of(2020, 1, 1), true, ACTOR_ID);
        ReflectionTestUtils.setField(vat, "id", VAT_ID);
        when(vatGroups.findAll()).thenReturn(List.of(vat));

        PriceListItem priceA = new PriceListItem(PRICE_LIST_ID, ITEM_A, UOM_ID, BigDecimal.ZERO,
            new BigDecimal("100"), LocalDate.of(2026, 1, 1));
        ReflectionTestUtils.setField(priceA, "id", 70L);
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
        assertThat(dto.items()).hasSize(2);
        var snapA = dto.items().get(0);
        assertThat(snapA.itemId()).isEqualTo(ITEM_A);
        assertThat(snapA.vatRate()).isEqualByComparingTo("0.18");
        assertThat(snapA.price()).isEqualByComparingTo("100");
        assertThat(snapA.qtyOnHand()).isEqualByComparingTo("50");
        assertThat(snapA.minSellPrice()).isEqualByComparingTo("45");
        assertThat(snapA.barcodes()).hasSize(1);
        assertThat(dto.items().get(1).price()).isEqualByComparingTo("0");
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
        assertThat(dto.balances().get(1).qtyOnHand()).isEqualByComparingTo("12.5");
    }

    // -----------------------------------------------------------------------
    // Cursor DTO wire-shape pin (§7.3 golden-payload)
    // -----------------------------------------------------------------------

    @Test
    void syncCursorDto_encodeDecodeRoundTrip() {
        SyncCursorDto original = new SyncCursorDto(1, 998877L);
        String token = original.encode();
        assertThat(token).isNotBlank().doesNotContain("=");  // URL-safe, no padding
        SyncCursorDto decoded = SyncCursorDto.decode(token);
        assertThat(decoded.version()).isEqualTo(1);
        assertThat(decoded.seq()).isEqualTo(998877L);
    }

    @Test
    void syncCursorDto_nullToken_returnsZero() {
        SyncCursorDto zero = SyncCursorDto.decode(null);
        assertThat(zero.seq()).isZero();
        assertThat(zero.version()).isEqualTo(1);
    }

    @Test
    void syncCursorDto_blankToken_returnsZero() {
        assertThat(SyncCursorDto.decode("   ").seq()).isZero();
    }

    @Test
    void syncCursorDto_malformedToken_throwsIllegalArgument() {
        assertThatThrownBy(() -> SyncCursorDto.decode("not-base64!!"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid sync cursor token");
    }

    // -----------------------------------------------------------------------
    // Customer dataset (GAP 1)
    // -----------------------------------------------------------------------

    @Test
    void pull_customerDataset_cursorZero_returnsActiveCustomers() {
        Party p = customerParty(501L, "CUST0001", "Mama Sara", PartyStatus.ACTIVE, 5L);
        Customer c = new Customer(501L);
        when(partyRepository.findCustomerPartiesByCompanyIdAndChangeSeqGreaterThan(
            eq(COMPANY_ID), eq(0L), any())).thenReturn(List.of(p));
        when(customerRepository.findById(501L)).thenReturn(java.util.Optional.of(c));

        SyncPullResultDto result = service.pull(null, "customer");

        assertThat(result.datasets()).containsKey("customer");
        var ds = result.datasets().get("customer");
        assertThat(ds.upserts()).hasSize(1);
        assertThat(ds.deletes()).isEmpty();

        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) ds.upserts().get(0);
        assertThat(row.get("uid")).isEqualTo(p.getUid());
        assertThat(row.get("code")).isEqualTo("CUST0001");
        assertThat(row.get("name")).isEqualTo("Mama Sara");
        assertThat(row.get("isWalkIn")).isEqualTo(false);
        assertThat(row.get("isActive")).isEqualTo(true);

        SyncCursorDto decoded = SyncCursorDto.decode(result.nextCursor());
        assertThat(decoded.seq()).isGreaterThanOrEqualTo(5L);
    }

    @Test
    void pull_customerDataset_archivedParty_surfacedAsDelete() {
        Party archived = customerParty(502L, "CUST0002", "Old Co", PartyStatus.ARCHIVED, 7L);
        when(partyRepository.findCustomerPartiesByCompanyIdAndChangeSeqGreaterThan(
            eq(COMPANY_ID), eq(0L), any())).thenReturn(List.of(archived));

        SyncPullResultDto result = service.pull(null, "customer");

        var ds = result.datasets().get("customer");
        assertThat(ds.upserts()).isEmpty();
        assertThat(ds.deletes()).containsExactly("502");
    }

    @Test
    void pull_customerDataset_changeSeqStamped_appearsOnDeltaPull() {
        // First pull: cursor = 0, change_seq = 10 → customer appears
        Party p = customerParty(503L, "CUST0003", "New Co", PartyStatus.ACTIVE, 10L);
        Customer c = new Customer(503L);
        when(partyRepository.findCustomerPartiesByCompanyIdAndChangeSeqGreaterThan(
            eq(COMPANY_ID), eq(0L), any())).thenReturn(List.of(p));
        when(customerRepository.findById(503L)).thenReturn(java.util.Optional.of(c));

        SyncPullResultDto first = service.pull(null, "customer");
        SyncCursorDto firstNext = SyncCursorDto.decode(first.nextCursor());
        assertThat(firstNext.seq()).isGreaterThanOrEqualTo(10L);

        // Second pull: cursor = seq(10), no new changes → empty
        when(partyRepository.findCustomerPartiesByCompanyIdAndChangeSeqGreaterThan(
            eq(COMPANY_ID), eq(firstNext.seq()), any())).thenReturn(List.of());

        SyncPullResultDto second = service.pull(first.nextCursor(), "customer");
        assertThat(second.datasets().get("customer").upserts()).isEmpty();
        assertThat(second.datasets().get("customer").deletes()).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private SyncPushRequestDto pushRequest(String clientOpId, Map<String, Object> payload) {
        return new SyncPushRequestDto("TILL-1", 1,
            List.of(syncOp(clientOpId, "POS_SALE", null, payload)));
    }

    private SyncOpDto syncOp(String clientOpId, String opType, String dependsOn,
                              Map<String, Object> payload) {
        return new SyncOpDto(clientOpId, opType, 1L,
            Instant.parse("2026-05-30T08:00:00Z"), dependsOn, payload);
    }

    private Map<String, Object> posSalePayload(String clientOpId) {
        // Field names must match PostPosSaleRequestDto record component names exactly
        // so that objectMapper.convertValue(map, PostPosSaleRequestDto.class) succeeds.
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("number",        "POS-1-20260530-00001");
        map.put("clientOpId",    clientOpId);
        map.put("tillSessionId", 99L);
        map.put("sectionId",     33L);
        map.put("customerId",    540L);
        map.put("supervisorId",  null);
        map.put("discountApproverId", null);
        map.put("saleAt",        "2026-05-30T08:00:00Z");
        map.put("headerDiscountAmount", null);
        map.put("lines", List.of(Map.of(
            "itemId", ITEM_A, "uomId", UOM_ID,
            "qty", "1", "unitPrice", "100", "vatGroupId", VAT_ID)));
        map.put("payments", List.of(Map.of(
            "method", "CASH", "amount", "118")));
        map.put("notes", null);
        return map;
    }

    private PosSaleDto stubSaleDto(String clientOpId, long id) {
        return new PosSaleDto(
            id, UidGenerator.next(), "POS-1-20260530-00001", clientOpId,
            200L, 100L, BRANCH_ID, COMPANY_ID,
            33L, 540L, ACTOR_ID, null,
            PosSaleKind.SALE, null,
            Instant.parse("2026-05-30T08:00:00Z"), Instant.now(), LocalDate.of(2026, 5, 30),
            new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("18"), new BigDecimal("118"),
            new BigDecimal("118"), BigDecimal.ZERO,
            PosSaleStatus.POSTED,
            null, null, null, null,
            null, null, null,   // fiscalStatus, fiscalVerificationCode, fiscalQrPayload
            List.of(), List.of()
        );
    }

    private PosSale stubSaleEntity(String clientOpId, long id) {
        PosSale s = new PosSale(
            "POS-1-20260530-00001", clientOpId,
            SESSION_ID, 1L, BRANCH_ID, COMPANY_ID,
            null, null, ACTOR_ID, null,
            PosSaleKind.SALE, Instant.parse("2026-05-30T08:00:00Z"),
            LocalDate.of(2026, 5, 30),
            new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000"),
            new BigDecimal("1000"), BigDecimal.ZERO, null
        );
        ReflectionTestUtils.setField(s, "id", id);
        ReflectionTestUtils.setField(s, "uid", UidGenerator.next());
        return s;
    }

    private TillSession openSession() {
        TillSession s = new TillSession(1L, BRANCH_ID, COMPANY_ID,
            LocalDate.of(2026, 5, 30), ACTOR_ID, new BigDecimal("200"));
        ReflectionTestUtils.setField(s, "id", SESSION_ID);
        ReflectionTestUtils.setField(s, "uid", UidGenerator.next());
        ReflectionTestUtils.setField(s, "companyId", COMPANY_ID);
        ReflectionTestUtils.setField(s, "clientOpId", "op-session-open");
        return s;
    }

    private Item activeItem(long id, String code, long groupId) {
        Item item = new Item(COMPANY_ID, code, code + "-name", ItemType.SELLABLE,
            groupId, UOM_ID, VAT_ID, ACTOR_ID);
        ReflectionTestUtils.setField(item, "id", id);
        ReflectionTestUtils.setField(item, "uid", UidGenerator.next());
        return item;
    }

    private Item seqItem(long id, long changeSeq) {
        Item item = activeItem(id, "SKU-" + id, 10L);
        ReflectionTestUtils.setField(item, "changeSeq", changeSeq);
        return item;
    }

    private Party customerParty(long id, String code, String name, PartyStatus status, long changeSeq) {
        Party p = new Party(COMPANY_ID, code, name, PartyCategory.BUSINESS, ACTOR_ID);
        p.setId(id);
        ReflectionTestUtils.setField(p, "uid", UidGenerator.next());
        ReflectionTestUtils.setField(p, "status", status);
        p.setChangeSeq(changeSeq);
        return p;
    }

    // -----------------------------------------------------------------------
    // ISSUE-SYNC-001: price dataset must include taxInclusive + priceListCode
    // -----------------------------------------------------------------------

    /**
     * The POS client needs {@code taxInclusive} and {@code priceListCode} from
     * the price dataset rows so it can apply the correct VAT formula locally.
     * Regression: old code omitted both fields, leaving the POS unable to
     * determine whether a synced price already includes VAT.
     */
    @Test
    void pull_priceDataset_includesTaxInclusiveAndPriceListCode() {
        com.orbix.engine.modules.catalog.domain.entity.PriceList pl =
            new com.orbix.engine.modules.catalog.domain.entity.PriceList(
                COMPANY_ID, "RETAIL", "Retail Price List", "TZS",
                java.time.LocalDate.of(2024, 1, 1), null, true, true, ACTOR_ID);
        ReflectionTestUtils.setField(pl, "id", PRICE_LIST_ID);

        PriceListItem pli = new PriceListItem(PRICE_LIST_ID, ITEM_A, UOM_ID, BigDecimal.ZERO,
            new BigDecimal("1300"), java.time.LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(pli, "id", 11L);
        ReflectionTestUtils.setField(pli, "changeSeq", 1000L);

        when(priceListRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of(pl));
        when(priceListItems.findByPriceListIdAndChangeSeqGreaterThan(
            eq(PRICE_LIST_ID), eq(0L), any())).thenReturn(List.of(pli));

        SyncPullResultDto result = service.pull(null, "price");

        assertThat(result.datasets()).containsKey("price");
        var ds = result.datasets().get("price");
        assertThat(ds.upserts()).hasSize(1);

        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) ds.upserts().get(0);
        assertThat(row).containsKey("taxInclusive");
        assertThat(row.get("taxInclusive")).isEqualTo(true);
        assertThat(row).containsKey("priceListCode");
        assertThat(row.get("priceListCode")).isEqualTo("RETAIL");
        assertThat((BigDecimal) row.get("price")).isEqualByComparingTo(new BigDecimal("1300"));
        assertThat(row.get("priceListId")).isEqualTo(String.valueOf(PRICE_LIST_ID));
    }

    @Test
    void pull_priceDataset_taxExclusive_includesTaxInclusiveFalse() {
        com.orbix.engine.modules.catalog.domain.entity.PriceList pl =
            new com.orbix.engine.modules.catalog.domain.entity.PriceList(
                COMPANY_ID, "WHOLE", "Wholesale", "TZS",
                java.time.LocalDate.of(2024, 1, 1), null, false, false, ACTOR_ID);
        ReflectionTestUtils.setField(pl, "id", PRICE_LIST_ID);

        PriceListItem pli = new PriceListItem(PRICE_LIST_ID, ITEM_A, UOM_ID, BigDecimal.ZERO,
            new BigDecimal("900"), java.time.LocalDate.of(2024, 1, 1));
        ReflectionTestUtils.setField(pli, "id", 12L);
        ReflectionTestUtils.setField(pli, "changeSeq", 2000L);

        when(priceListRepo.findByCompanyId(COMPANY_ID)).thenReturn(List.of(pl));
        when(priceListItems.findByPriceListIdAndChangeSeqGreaterThan(
            eq(PRICE_LIST_ID), eq(0L), any())).thenReturn(List.of(pli));

        SyncPullResultDto result = service.pull(null, "price");

        @SuppressWarnings("unchecked")
        var row = (java.util.Map<String, Object>) result.datasets().get("price").upserts().get(0);
        assertThat(row.get("taxInclusive")).isEqualTo(false);
        assertThat(row.get("priceListCode")).isEqualTo("WHOLE");
    }

    // -----------------------------------------------------------------------
    // ISSUE-SYNC-003: applyPosSale TOCTOU — concurrent replay must return DUPLICATE
    // -----------------------------------------------------------------------

    /**
     * If the {@code uk_pos_sale_client_op} constraint fires during insert
     * (concurrent replay, not caught by the sequential pre-check), the service
     * must reload the existing row and return DUPLICATE — not propagate the
     * DataIntegrityViolationException as REJECTED or 500.
     */
    @Test
    void push_posSale_constraintViolation_returnsDuplicate() {
        PosSale prior = stubSaleEntity("op-race", 9999L);

        // Pre-check: first call finds nothing (race window is open)
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-race"))
            .thenReturn(Optional.empty())
            // Reload after constraint hit: winner's row is now visible
            .thenReturn(Optional.of(prior));

        // Insert attempt hits the unique constraint
        when(posSaleService.post(any()))
            .thenThrow(new org.springframework.dao.DataIntegrityViolationException(
                "uk_pos_sale_client_op constraint violation"));

        SyncPushResultDto result = service.pushBatch(pushRequest("op-race", posSalePayload("op-race")));

        SyncPushResultDto.OpResultDto r = result.results().get(0);
        assertThat(r.verdict()).isEqualTo("DUPLICATE");
        assertThat(r.serverEntityId()).isEqualTo(String.valueOf(prior.getId()));
        // DUPLICATE is not counted as rejected
        assertThat(result.batchRejectedCount()).isZero();
    }

    /**
     * Sequential retry (pre-check finds the existing row immediately) still
     * returns DUPLICATE without calling post() again.
     */
    @Test
    void push_posSale_sequentialRetry_returnsDuplicateWithoutPost() {
        PosSale prior = stubSaleEntity("op-seq-dup", 8888L);
        when(sales.findByCompanyIdAndClientOpId(COMPANY_ID, "op-seq-dup"))
            .thenReturn(Optional.of(prior));

        SyncPushResultDto result = service.pushBatch(pushRequest("op-seq-dup", posSalePayload("op-seq-dup")));

        assertThat(result.results().get(0).verdict()).isEqualTo("DUPLICATE");
        verify(posSaleService, never()).post(any());
    }
}
