package com.orbix.engine.modules.pos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.ItemBarcode;
import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.domain.enums.ItemStatus;
import com.orbix.engine.modules.catalog.repository.ItemBarcodeRepository;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.PriceListItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.party.domain.entity.Customer;
import com.orbix.engine.modules.party.domain.entity.Party;
import com.orbix.engine.modules.party.domain.enums.PartyStatus;
import com.orbix.engine.modules.party.repository.CustomerRepository;
import com.orbix.engine.modules.party.repository.PartyRepository;
import com.orbix.engine.modules.common.service.RequestContext;
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
import com.orbix.engine.modules.pos.domain.entity.CashPickup;
import com.orbix.engine.modules.pos.domain.entity.PettyCash;
import com.orbix.engine.modules.pos.domain.entity.PosSale;
import com.orbix.engine.modules.pos.domain.entity.TillSession;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.domain.enums.TillSessionStatus;
import com.orbix.engine.modules.pos.repository.CashPickupRepository;
import com.orbix.engine.modules.pos.repository.PettyCashRepository;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Offline-sync spine implementation (US-POS-017 / US-POS-018).
 * Design: docs/design/slice-sync-spine.md.
 *
 * <p>Push is intentionally NOT @Transactional at the batch level — each op
 * runs in its own REQUIRED tx so one REJECTED op doesn't roll back its siblings.
 *
 * <p>Idempotency for POS_SALE is enforced by the existing uk_pos_sale_client_op
 * constraint + PosSaleService.post's own pre-check. Other ops (TILL_SESSION_OPEN,
 * CASH_PICKUP, PETTY_CASH) use the new uk_*_client_op constraints added in V78.
 *
 * <p>Pull uses a monotonic change_seq stamped by the service write path.
 * Tombstone retention: orbix.sync.tombstone-days (PM-approved default 90 days).
 */
@Service
@RequiredArgsConstructor
public class SyncServiceImpl implements SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncServiceImpl.class);

    // Dataset name constants
    static final String DS_CATALOG  = "catalog";
    static final String DS_PRICE    = "price";
    static final String DS_BALANCE  = "balance";
    static final String DS_CUSTOMER = "customer";

    private final PosSaleService posSaleService;
    private final TillSessionService tillSessionService;
    private final TillSessionRepository sessions;
    private final CashPickupRepository pickups;
    private final PettyCashRepository pettyCash;
    private final PosSaleRepository sales;
    private final ItemRepository items;
    private final ItemBarcodeRepository barcodes;
    private final VatGroupRepository vatGroups;
    private final PriceListItemRepository priceListItems;
    private final ItemBranchBalanceRepository balances;
    private final PartyRepository partyRepository;
    private final CustomerRepository customerRepository;
    private final RequestContext context;
    private final ObjectMapper objectMapper;

    /** Tunable via orbix.sync.push-batch-max (PM-approved default 500). */
    @Value("${orbix.sync.push-batch-max:500}")
    private int pushBatchMax;

    /** Tunable via orbix.sync.pull-page-size (default 2000 rows/dataset). */
    @Value("${orbix.sync.pull-page-size:2000}")
    private int pullPageSize;

    // -----------------------------------------------------------------------
    // Push
    // -----------------------------------------------------------------------

    /**
     * Deliberately NOT @Transactional — each op runs in its own REQUIRED tx.
     * One bad op never rolls back its batch siblings.
     * Design §2.3 + §2.4.
     */
    @Override
    public SyncPushResultDto pushBatch(SyncPushRequestDto request) {
        if (request.ops().size() > pushBatchMax) {
            throw new IllegalArgumentException(
                "Batch size " + request.ops().size() + " exceeds server maximum " + pushBatchMax
                    + " — split into smaller batches (orbix.sync.push-batch-max)");
        }

        List<SyncPushResultDto.OpResultDto> results = new ArrayList<>(request.ops().size());
        // Track which clientOpIds have been ACCEPTED or DUPLICATE in this batch
        // so dependsOn resolution works within a single batch.
        Set<String> settled = new HashSet<>();
        int accepted = 0;
        int rejected = 0;

        for (SyncOpDto op : request.ops()) {
            // DEFERRED — dependsOn not yet settled
            if (op.dependsOn() != null && !settled.contains(op.dependsOn())) {
                log.debug("Sync DEFERRED clientOpId={} dependsOn={}", op.clientOpId(), op.dependsOn());
                results.add(SyncPushResultDto.OpResultDto.deferred(op.clientOpId()));
                // Don't count deferred as rejected; client re-pushes
                continue;
            }

            SyncPushResultDto.OpResultDto result = applyOp(op);
            results.add(result);

            switch (result.verdict()) {
                case "ACCEPTED" -> { accepted++; settled.add(op.clientOpId()); }
                case "DUPLICATE" -> settled.add(op.clientOpId());
                case "REJECTED" -> rejected++;
                default -> { /* DEFERRED handled above */ }
            }
        }

        return new SyncPushResultDto(accepted, rejected, Instant.now(), false, results);
    }

    /**
     * Apply a single op in its own REQUIRED tx.
     * Exceptions are caught and turned into REJECTED verdicts.
     */
    @Transactional
    SyncPushResultDto.OpResultDto applyOp(SyncOpDto op) {
        try {
            return switch (op.opType()) {
                case "POS_SALE"          -> applyPosSale(op);
                case "TILL_SESSION_OPEN" -> applyTillSessionOpen(op);
                case "CASH_PICKUP"       -> applyCashPickup(op);
                case "PETTY_CASH"        -> applyPettyCash(op);
                case "POS_SALE_VOID"     -> applyPosSaleVoid(op);
                case "TILL_SESSION_CLOSE" -> applyTillSessionClose(op);
                case "FIELD_SALE"        -> {
                    // WMS van-stock FIELD_SALE: out of scope for POS pilot — stub/guard.
                    // PM decision: counter-only POS first. Tracked in US-WMS-005.
                    yield SyncPushResultDto.OpResultDto.rejected(
                        op.clientOpId(), "FIELD_SALE_NOT_SUPPORTED",
                        "FIELD_SALE is not supported in the POS pilot (WMS van-stock drain is out of scope)");
                }
                default -> SyncPushResultDto.OpResultDto.rejected(
                    op.clientOpId(), "UNKNOWN_OP_TYPE",
                    "Unknown opType: " + op.opType());
            };
        } catch (RuntimeException ex) {
            log.warn("Sync REJECTED clientOpId={} opType={} reason={}",
                op.clientOpId(), op.opType(), ex.getMessage());
            return SyncPushResultDto.OpResultDto.rejected(op.clientOpId(), "BUSINESS_RULE_VIOLATION",
                ex.getMessage());
        }
    }

    /**
     * Apply a POS_SALE op.
     *
     * <p>Idempotency is enforced by the unique {@code (company_id, client_op_id)} constraint
     * on {@code pos_sale} — we do NOT pre-check then insert (TOCTOU race). Instead:
     * <ol>
     *   <li>Attempt the insert via {@code posSaleService.post}.</li>
     *   <li>If the constraint fires ({@link DataIntegrityViolationException}), reload the
     *       existing row and return DUPLICATE with its original ids.</li>
     * </ol>
     * This means two concurrent replays of the same {@code clientOpId} can never both
     * return ACCEPTED — the second one always loses the constraint race and becomes DUPLICATE.
     */
    private SyncPushResultDto.OpResultDto applyPosSale(SyncOpDto op) {
        PostPosSaleRequestDto saleRequest = convertPayload(op.payload(), PostPosSaleRequestDto.class);
        Long companyId = context.companyId();
        // Fast-path: already persisted (e.g. sequential retry). PosSaleService.post
        // checks the same key first and returns the existing row without a second insert.
        Optional<PosSale> existing = sales.findByCompanyIdAndClientOpId(companyId, op.clientOpId());
        if (existing.isPresent()) {
            PosSale prior = existing.get();
            return SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(),
                prior.getId(), prior.getUid(), prior.getNumber());
        }
        try {
            PosSaleDto posted = posSaleService.post(saleRequest);
            return SyncPushResultDto.OpResultDto.accepted(op.clientOpId(),
                posted.id(), posted.uid(), posted.number());
        } catch (DataIntegrityViolationException ex) {
            // Concurrent replay hit the uk_pos_sale_client_op constraint — reload and
            // return DUPLICATE so both callers get a consistent response.
            log.debug("applyPosSale constraint hit for clientOpId={}, reloading as DUPLICATE",
                op.clientOpId());
            return sales.findByCompanyIdAndClientOpId(companyId, op.clientOpId())
                .map(prior -> SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(),
                    prior.getId(), prior.getUid(), prior.getNumber()))
                .orElseThrow(() -> ex); // constraint fired but row not found — rethrow original
        }
    }

    @Transactional
    SyncPushResultDto.OpResultDto applyTillSessionOpen(SyncOpDto op) {
        Long companyId = context.companyId();
        // Idempotency pre-check
        Optional<TillSession> existing = sessions.findByCompanyIdAndClientOpId(companyId, op.clientOpId());
        if (existing.isPresent()) {
            TillSession s = existing.get();
            return SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(),
                s.getId(), s.getUid(), null);
        }
        Long tillId       = payloadLong(op, "tillId");
        BigDecimal float_ = payloadDecimal(op, "openingFloatAmount");

        // Delegate to TillSessionService.open — same business rules as the online path
        // (DayGuard, CashLedger float posting, event publishing). This is a within-module
        // call (pos→pos) which ArchUnit permits. TillSessionService.open is @Transactional
        // REQUIRED, so it participates in this tx and the clientOpId stamp below is atomic.
        com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto openReq =
            new com.orbix.engine.modules.pos.domain.dto.OpenTillSessionRequestDto(tillId, float_);
        com.orbix.engine.modules.pos.domain.dto.TillSessionDto dto = tillSessionService.open(openReq);

        // Stamp clientOpId on the freshly-saved session so the idempotency key is durable.
        TillSession saved = sessions.findById(dto.id())
            .orElseThrow(() -> new IllegalStateException("Session just opened not found: " + dto.id()));
        saved.setClientOpId(op.clientOpId());
        sessions.save(saved);

        return SyncPushResultDto.OpResultDto.accepted(op.clientOpId(), dto.id(), dto.uid(), null);
    }

    private SyncPushResultDto.OpResultDto applyCashPickup(SyncOpDto op) {
        Long companyId = context.companyId();
        Optional<CashPickup> existing = pickups.findByCompanyIdAndClientOpId(companyId, op.clientOpId());
        if (existing.isPresent()) {
            CashPickup p = existing.get();
            return SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(), p.getId(), p.getUid(), null);
        }
        Long tillSessionId = payloadLong(op, "tillSessionId");
        BigDecimal amount  = payloadDecimal(op, "amount");
        Long authorisedBy  = payloadLong(op, "authorisedBy");
        String note        = payloadString(op, "note");

        TillSession session = sessions.findById(tillSessionId)
            .filter(s -> s.getCompanyId().equals(companyId))
            .orElseThrow(() -> new java.util.NoSuchElementException("Till session not found: " + tillSessionId));

        CashPickup pickup = new CashPickup(
            session.getId(), companyId, session.getBranchId(), session.getBusinessDate(),
            amount, op.occurredAt(), context.userId(), authorisedBy, note
        );
        pickup.setClientOpId(op.clientOpId());
        CashPickup saved = pickups.save(pickup);
        return SyncPushResultDto.OpResultDto.accepted(op.clientOpId(), saved.getId(), saved.getUid(), null);
    }

    private SyncPushResultDto.OpResultDto applyPettyCash(SyncOpDto op) {
        Long companyId = context.companyId();
        Optional<PettyCash> existing = pettyCash.findByCompanyIdAndClientOpId(companyId, op.clientOpId());
        if (existing.isPresent()) {
            PettyCash p = existing.get();
            return SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(), p.getId(), p.getUid(), null);
        }
        Long tillSessionId = payloadLong(op, "tillSessionId");
        BigDecimal amount  = payloadDecimal(op, "amount");
        Long authorisedBy  = payloadLong(op, "authorisedBy");
        String categoryStr = payloadString(op, "category");
        String paidTo      = payloadString(op, "paidTo");
        String description = payloadString(op, "description");

        TillSession session = sessions.findById(tillSessionId)
            .filter(s -> s.getCompanyId().equals(companyId))
            .orElseThrow(() -> new java.util.NoSuchElementException("Till session not found: " + tillSessionId));

        com.orbix.engine.modules.pos.domain.enums.PettyCashCategory category;
        try {
            category = com.orbix.engine.modules.pos.domain.enums.PettyCashCategory.valueOf(categoryStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unknown PettyCashCategory: " + categoryStr);
        }

        PettyCash pc = new PettyCash(
            session.getId(), companyId, session.getBranchId(), session.getBusinessDate(),
            amount, op.occurredAt(), category, paidTo,
            context.userId(), authorisedBy, description, null
        );
        pc.setClientOpId(op.clientOpId());
        PettyCash saved = pettyCash.save(pc);
        return SyncPushResultDto.OpResultDto.accepted(op.clientOpId(), saved.getId(), saved.getUid(), null);
    }

    private SyncPushResultDto.OpResultDto applyPosSaleVoid(SyncOpDto op) {
        // POS_SALE_VOID: validate the clientOpId of the void op and call voidSale.
        String originalClientOpId = payloadString(op, "originalClientOpId");
        String reason             = payloadString(op, "reason");
        Long companyId = context.companyId();

        if (originalClientOpId == null) {
            throw new IllegalArgumentException("POS_SALE_VOID payload must contain originalClientOpId");
        }
        PosSale sale = sales.findByCompanyIdAndClientOpId(companyId, originalClientOpId)
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Sale with clientOpId " + originalClientOpId + " not found — push the sale first"));

        // Idempotency for the void itself: if already voided return DUPLICATE
        if (sale.getStatus() == PosSaleStatus.VOIDED) {
            return SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(),
                sale.getId(), sale.getUid(), sale.getNumber());
        }

        PosSaleDto voided = posSaleService.voidSale(sale.getUid(),
            new com.orbix.engine.modules.pos.domain.dto.VoidPosSaleRequestDto(reason));
        return SyncPushResultDto.OpResultDto.accepted(op.clientOpId(),
            voided.id(), voided.uid(), voided.number());
    }

    private SyncPushResultDto.OpResultDto applyTillSessionClose(SyncOpDto op) {
        // TILL_SESSION_CLOSE via push batch: simplified path.
        // For full reconciliation use POST /api/v1/sync/till-session/close.
        String tillSessionClientOpId = payloadString(op, "tillSessionClientOpId");
        BigDecimal declaredCash       = payloadDecimal(op, "declaredCash");
        Long companyId = context.companyId();

        if (tillSessionClientOpId == null) {
            throw new IllegalArgumentException("TILL_SESSION_CLOSE payload must contain tillSessionClientOpId");
        }
        TillSession session = sessions.findByCompanyIdAndClientOpId(companyId, tillSessionClientOpId)
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Till session with clientOpId " + tillSessionClientOpId + " not found"));

        if (session.getStatus() == TillSessionStatus.CLOSED
                || session.getStatus() == TillSessionStatus.RECONCILED) {
            return SyncPushResultDto.OpResultDto.duplicate(op.clientOpId(),
                session.getId(), session.getUid(), null);
        }

        // Call the service via uid
        com.orbix.engine.modules.pos.domain.dto.TillSessionDto closed = closeSession(session.getUid(), declaredCash);
        return SyncPushResultDto.OpResultDto.accepted(op.clientOpId(),
            closed.id(), closed.uid(), null);
    }

    // -----------------------------------------------------------------------
    // Pull
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public SyncPullResultDto pull(String cursorToken, String datasets) {
        SyncCursorDto cursor = SyncCursorDto.decode(cursorToken);
        Set<String> requested = parseDatasets(datasets);
        return buildPullResult(cursor, requested, false);
    }

    @Override
    @Transactional(readOnly = true)
    public SyncPullResultDto bootstrap(String datasets) {
        // Bootstrap = pull from zero cursor, no paging cap
        Set<String> requested = parseDatasets(datasets);
        return buildPullResult(SyncCursorDto.ZERO, requested, true);
    }

    private SyncPullResultDto buildPullResult(SyncCursorDto cursor, Set<String> requested, boolean bootstrap) {
        Long companyId = context.companyId();
        Long branchId  = context.requireBranchId();
        long fromSeq   = cursor.seq();
        PageRequest page = bootstrap
            ? PageRequest.of(0, Integer.MAX_VALUE)
            : PageRequest.of(0, pullPageSize);

        Map<String, SyncPullResultDto.DatasetDto> datasets = new LinkedHashMap<>();
        long maxSeq = fromSeq;
        boolean hasMore = false;

        if (requested.isEmpty() || requested.contains(DS_CATALOG)) {
            DatasetResult<Object> cat = buildCatalogDataset(companyId, fromSeq, page);
            datasets.put(DS_CATALOG, new SyncPullResultDto.DatasetDto(cat.upserts(), cat.deletes()));
            if (cat.maxSeq() > maxSeq) maxSeq = cat.maxSeq();
            if (cat.hasMore()) hasMore = true;
        }
        if (requested.isEmpty() || requested.contains(DS_PRICE)) {
            // price dataset is branch-agnostic in v1 (pull by company+all lists)
            // For v1 we pull all price_list_items changed since cursor for the company.
            DatasetResult<Object> price = buildPriceDataset(companyId, fromSeq, page);
            datasets.put(DS_PRICE, new SyncPullResultDto.DatasetDto(price.upserts(), price.deletes()));
            if (price.maxSeq() > maxSeq) maxSeq = price.maxSeq();
            if (price.hasMore()) hasMore = true;
        }
        if (requested.isEmpty() || requested.contains(DS_BALANCE)) {
            DatasetResult<Object> bal = buildBalanceDataset(branchId, fromSeq);
            datasets.put(DS_BALANCE, new SyncPullResultDto.DatasetDto(bal.upserts(), bal.deletes()));
            if (bal.maxSeq() > maxSeq) maxSeq = bal.maxSeq();
        }
        if (requested.isEmpty() || requested.contains(DS_CUSTOMER)) {
            DatasetResult<Object> cust = buildCustomerDataset(companyId, fromSeq, page);
            datasets.put(DS_CUSTOMER, new SyncPullResultDto.DatasetDto(cust.upserts(), cust.deletes()));
            if (cust.maxSeq() > maxSeq) maxSeq = cust.maxSeq();
            if (cust.hasMore()) hasMore = true;
        }

        SyncCursorDto nextCursor = new SyncCursorDto(1, maxSeq);
        return new SyncPullResultDto(Instant.now(), nextCursor.encode(), hasMore, false, datasets);
    }

    private DatasetResult<Object> buildCatalogDataset(Long companyId, long fromSeq, PageRequest page) {
        List<Item> changed = items.findByCompanyIdAndChangeSeqGreaterThan(companyId, fromSeq, page);
        boolean hasMore = changed.size() == page.getPageSize() && page.getPageSize() != Integer.MAX_VALUE;
        long maxSeq = fromSeq;

        List<Object> upserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();

        for (Item item : changed) {
            if (item.getChangeSeq() != null && item.getChangeSeq() > maxSeq) {
                maxSeq = item.getChangeSeq();
            }
            if (item.getStatus() == ItemStatus.ARCHIVED) {
                deletes.add(String.valueOf(item.getId()));
            } else {
                List<ItemBarcode> bcs = barcodes.findByItemId(item.getId());
                upserts.add(buildItemSnapshot(item, bcs));
            }
        }
        return new DatasetResult<>(upserts, deletes, maxSeq, hasMore);
    }

    private Map<String, Object> buildItemSnapshot(Item item, List<ItemBarcode> bcs) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id",            String.valueOf(item.getId()));
        m.put("uid",           item.getUid());
        m.put("code",          item.getCode());
        m.put("name",          item.getName());
        m.put("type",          item.getType().name());
        m.put("uomId",         String.valueOf(item.getUomId()));
        m.put("vatGroupId",    String.valueOf(item.getVatGroupId()));
        m.put("isWeighed",     item.isWeighed());
        m.put("isBatchTracked", item.isBatchTracked());
        m.put("minSellPrice",  item.getMinSellPrice());
        m.put("status",        item.getStatus().name());
        m.put("changeSeq",     item.getChangeSeq());
        List<Map<String, Object>> bcList = new ArrayList<>();
        for (ItemBarcode bc : bcs) {
            Map<String, Object> b = new LinkedHashMap<>();
            b.put("barcode",     bc.getBarcode());
            b.put("barcodeType", bc.getBarcodeType() != null ? bc.getBarcodeType().name() : null);
            b.put("packUomId",   bc.getPackUomId() != null ? String.valueOf(bc.getPackUomId()) : null);
            b.put("packQty",     bc.getPackQty());
            bcList.add(b);
        }
        m.put("barcodes", bcList);
        return m;
    }

    private DatasetResult<Object> buildPriceDataset(Long companyId, long fromSeq, PageRequest page) {
        // In v1 we pull all price_list_items for the company that changed.
        // price_list_item has its own change_seq; no company_id column,
        // so we filter via price_list.company_id join — for v1 use a simpler
        // approach: pull all company price lists then their changed rows.
        //
        // NOTE: PriceListItemRepository.findByPriceListIdAndChangeSeqGreaterThan
        // works per-list. For v1 we fetch all company lists then union changed rows.
        // This is acceptable for reference-data size (price lists are not large).
        List<Object> upserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();
        long maxSeq = fromSeq;
        boolean hasMore = false;

        // Get price lists for company
        List<com.orbix.engine.modules.catalog.domain.entity.PriceList> lists =
            priceListRepository().findByCompanyId(companyId);
        for (com.orbix.engine.modules.catalog.domain.entity.PriceList pl : lists) {
            List<PriceListItem> changed = priceListItems
                .findByPriceListIdAndChangeSeqGreaterThan(pl.getId(), fromSeq, page);
            if (!changed.isEmpty() && changed.size() == page.getPageSize()
                    && page.getPageSize() != Integer.MAX_VALUE) {
                hasMore = true;
            }
            for (PriceListItem pli : changed) {
                if (pli.getChangeSeq() != null && pli.getChangeSeq() > maxSeq) {
                    maxSeq = pli.getChangeSeq();
                }
                // Closed rows (validTo set) surface as deletes so client removes stale prices
                if (pli.getValidTo() != null) {
                    deletes.add(String.valueOf(pli.getId()));
                } else {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id",            String.valueOf(pli.getId()));
                    m.put("priceListId",   String.valueOf(pli.getPriceListId()));
                    m.put("priceListCode", pl.getCode());
                    m.put("taxInclusive",  pl.isTaxInclusive());
                    m.put("itemId",        String.valueOf(pli.getItemId()));
                    m.put("uomId",         String.valueOf(pli.getUomId()));
                    m.put("minQty",        pli.getMinQty());
                    m.put("price",         pli.getPrice());
                    m.put("validFrom",     pli.getValidFrom().toString());
                    m.put("changeSeq",     pli.getChangeSeq());
                    upserts.add(m);
                }
            }
        }
        return new DatasetResult<>(upserts, deletes, maxSeq, hasMore);
    }

    private DatasetResult<Object> buildBalanceDataset(Long branchId, long fromSeq) {
        // Balance rows don't carry change_seq in v1 — full snapshot for the branch.
        // This matches the existing balanceSnapshot behaviour. Adding change_seq to
        // item_branch_balance is a future optimisation; for v1 the balance dataset
        // is always a full replace (no deletes, no cursor advancement from this dataset).
        List<Object> upserts = new ArrayList<>();
        for (ItemBranchBalance b : balances.findByBranchId(branchId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId",    String.valueOf(b.getItemId()));
            m.put("branchId",  String.valueOf(b.getBranchId()));
            m.put("qtyOnHand", b.getQtyOnHand());
            upserts.add(m);
        }
        return new DatasetResult<>(upserts, List.of(), fromSeq, false);
    }

    /**
     * Customer dataset.
     *
     * <p>Fields served per row:
     * <ul>
     *   <li>id, uid, code, name — party identity</li>
     *   <li>isWalkIn — POS uses this to auto-select the walk-in customer</li>
     *   <li>isActive — false when party.status = ARCHIVED; POS hides inactive customers</li>
     *   <li>creditLimitAmount — customer credit ceiling (from customer role row)</li>
     * </ul>
     *
     * <p>Current balance is omitted — it requires a cross-module AR query (sales module)
     * and is not cheap to compute per-row in a bulk pull. The POS performs credit checks
     * online at sale-post time; offline credit enforcement is a future slice.
     *
     * <p>Archived parties surface in the deletes array (party.status = ARCHIVED) so the
     * POS can remove stale rows. The cursor advances from the party.change_seq column.
     */
    private DatasetResult<Object> buildCustomerDataset(Long companyId, long fromSeq, PageRequest page) {
        List<Party> changed = partyRepository.findCustomerPartiesByCompanyIdAndChangeSeqGreaterThan(
            companyId, fromSeq, page);
        boolean hasMore = changed.size() == page.getPageSize() && page.getPageSize() != Integer.MAX_VALUE;
        long maxSeq = fromSeq;

        List<Object> upserts = new ArrayList<>();
        List<String> deletes = new ArrayList<>();

        for (Party party : changed) {
            if (party.getChangeSeq() != null && party.getChangeSeq() > maxSeq) {
                maxSeq = party.getChangeSeq();
            }
            if (party.getStatus() == PartyStatus.ARCHIVED) {
                deletes.add(String.valueOf(party.getId()));
            } else {
                Customer customer = customerRepository.findById(party.getId()).orElse(null);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",                String.valueOf(party.getId()));
                m.put("uid",               party.getUid());
                m.put("code",              party.getCode());
                m.put("name",              party.getName());
                m.put("isWalkIn",          customer != null && customer.isWalkIn());
                m.put("isActive",          party.getStatus() == PartyStatus.ACTIVE);
                m.put("creditLimitAmount", customer != null ? customer.getCreditLimitAmount() : null);
                m.put("changeSeq",         party.getChangeSeq());
                upserts.add(m);
            }
        }
        return new DatasetResult<>(upserts, deletes, maxSeq, hasMore);
    }

    // -----------------------------------------------------------------------
    // Till-session close / reconciliation
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public TillSessionCloseResultDto closeTillSession(TillSessionCloseRequestDto request) {
        Long companyId = context.companyId();

        TillSession session = sessions
            .findByCompanyIdAndClientOpId(companyId, request.tillSessionClientOpId())
            .orElseThrow(() -> new java.util.NoSuchElementException(
                "Till session not found for clientOpId: " + request.tillSessionClientOpId()));

        // Already closed — idempotent close returns confirmed set from current state.
        if (session.getStatus() == TillSessionStatus.CLOSED
                || session.getStatus() == TillSessionStatus.RECONCILED) {
            return buildClosedResult(session, request.manifest().clientOpIds());
        }

        // --- Validate manifest ---
        List<String> allSessionOpIds = collectSessionClientOpIds(session.getId(), companyId);
        List<String> clientClaimed   = request.manifest().clientOpIds();

        Set<String> serverSet = new HashSet<>(allSessionOpIds);
        Set<String> clientSet = new HashSet<>(clientClaimed);

        List<String> missing     = clientClaimed.stream().filter(id -> !serverSet.contains(id)).toList();
        List<String> unexpected  = allSessionOpIds.stream().filter(id -> !clientSet.contains(id)).toList();

        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            return new TillSessionCloseResultDto(
                session.getUid(),
                "RECONCILE_INCOMPLETE",
                session.getOpeningFloatAmount(),
                BigDecimal.ZERO,  // not computed until manifest is clean
                request.declaredCash(),
                BigDecimal.ZERO,
                List.of(),
                missing,
                unexpected,
                null
            );
        }

        // --- Manifest matches: compute expected cash and close ---
        BigDecimal expectedCash = computeExpectedCash(session);
        BigDecimal variance = request.declaredCash().subtract(expectedCash);

        session.close(expectedCash, request.declaredCash(), context.userId(), null, null);
        sessions.save(session);

        return new TillSessionCloseResultDto(
            session.getUid(),
            "CLOSED",
            session.getOpeningFloatAmount(),
            expectedCash,
            request.declaredCash(),
            variance,
            allSessionOpIds,
            List.of(),
            List.of(),
            session.getZReportObjectKey()
        );
    }

    private TillSessionCloseResultDto buildClosedResult(TillSession session, List<String> clientOpIds) {
        List<String> confirmed = collectSessionClientOpIds(session.getId(), session.getCompanyId());
        return new TillSessionCloseResultDto(
            session.getUid(),
            "CLOSED",
            session.getOpeningFloatAmount(),
            session.getExpectedCashAmount() != null ? session.getExpectedCashAmount() : BigDecimal.ZERO,
            session.getDeclaredCashAmount() != null ? session.getDeclaredCashAmount() : BigDecimal.ZERO,
            session.getVarianceAmount()     != null ? session.getVarianceAmount()     : BigDecimal.ZERO,
            confirmed,
            List.of(),
            List.of(),
            session.getZReportObjectKey()
        );
    }

    /**
     * Collect all clientOpIds the server holds for a given till session.
     * Includes: pos_sale, cash_pickup, petty_cash clientOpIds (non-null only).
     */
    private List<String> collectSessionClientOpIds(Long sessionId, Long companyId) {
        List<String> ids = new ArrayList<>();
        // Sales
        sales.findByTillSessionIdOrderByIdAsc(sessionId).stream()
            .filter(s -> s.getClientOpId() != null)
            .map(PosSale::getClientOpId)
            .forEach(ids::add);
        // Pickups
        pickups.findByTillSessionIdOrderByAtAsc(sessionId).stream()
            .filter(p -> p.getClientOpId() != null)
            .map(CashPickup::getClientOpId)
            .forEach(ids::add);
        // Petty cash
        pettyCash.findByTillSessionIdOrderByAtAsc(sessionId).stream()
            .filter(p -> p.getClientOpId() != null)
            .map(PettyCash::getClientOpId)
            .forEach(ids::add);
        return ids;
    }

    /**
     * Expected drawer = opening_float + cash sales - cash refunds - pickups - petty_cash.
     * Mirrors TillSessionServiceImpl.computeExpectedCash.
     */
    private BigDecimal computeExpectedCash(TillSession session) {
        BigDecimal expected = session.getOpeningFloatAmount();
        for (PosSale sale : sales.findByTillSessionIdOrderByIdAsc(session.getId())) {
            if (sale.getStatus() == PosSaleStatus.POSTED) {
                // Simplified: count total_amount for SALE, subtract for REFUND.
                // Full cash-method breakdown lives in TillSessionServiceImpl.
                // For sync reconciliation this approximation is acceptable in v1.
                BigDecimal contrib = sale.getKind() == com.orbix.engine.modules.pos.domain.enums.PosSaleKind.REFUND
                    ? sale.getTotalAmount().negate()
                    : sale.getTotalAmount();
                expected = expected.add(contrib);
            }
        }
        expected = expected.subtract(pickups.sumForSession(session.getId()));
        expected = expected.subtract(pettyCash.sumForSession(session.getId()));
        return expected;
    }

    // -----------------------------------------------------------------------
    // Legacy snapshot endpoints (backward-compat — superseded by pull/bootstrap)
    // -----------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public CatalogSnapshotDto catalogSnapshot(Long branchId, Long priceListId) {
        Long companyId = context.companyId();
        List<Item> activeItems = items.findByCompanyIdAndStatusOrderByIdAsc(companyId, ItemStatus.ACTIVE);

        Map<Long, VatGroup> vatById = new HashMap<>();
        vatGroups.findAll().forEach(v -> vatById.put(v.getId(), v));

        Map<Long, PriceListItem> priceByItem = new HashMap<>();
        priceListItems.findByPriceListIdAndValidToIsNull(priceListId)
            .forEach(p -> priceByItem.put(p.getItemId(), p));

        Map<Long, BigDecimal> onHandByItem = new HashMap<>();
        for (ItemBranchBalance b : balances.findByBranchId(branchId)) {
            onHandByItem.put(b.getItemId(), b.getQtyOnHand());
        }

        List<CatalogSnapshotDto.ItemSnapshot> snapshots = new ArrayList<>(activeItems.size());
        for (Item item : activeItems) {
            VatGroup vat = vatById.get(item.getVatGroupId());
            PriceListItem priceRow = priceByItem.get(item.getId());
            BigDecimal price = priceRow != null ? priceRow.getPrice() : BigDecimal.ZERO;
            BigDecimal qty = onHandByItem.getOrDefault(item.getId(), BigDecimal.ZERO);
            List<CatalogSnapshotDto.BarcodeSnapshot> barcodeSnapshots = new ArrayList<>();
            for (ItemBarcode bc : barcodes.findByItemId(item.getId())) {
                barcodeSnapshots.add(new CatalogSnapshotDto.BarcodeSnapshot(
                    bc.getBarcode(),
                    bc.getBarcodeType() != null ? bc.getBarcodeType().name() : null,
                    bc.getPackUomId(),
                    bc.getPackQty()
                ));
            }
            snapshots.add(new CatalogSnapshotDto.ItemSnapshot(
                item.getId(),
                item.getCode(),
                item.getName(),
                item.getType().name(),
                item.getUomId(),
                item.getVatGroupId(),
                vat != null ? vat.getRate() : BigDecimal.ZERO,
                item.isWeighed(),
                item.isBatchTracked(),
                item.getMinSellPrice(),
                price,
                qty,
                barcodeSnapshots
            ));
        }
        return new CatalogSnapshotDto(Instant.now(), branchId, priceListId, snapshots);
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSnapshotDto balanceSnapshot(Long branchId) {
        List<BalanceSnapshotDto.Row> rows = balances.findByBranchId(branchId).stream()
            .map(b -> new BalanceSnapshotDto.Row(b.getItemId(), b.getQtyOnHand()))
            .toList();
        return new BalanceSnapshotDto(Instant.now(), branchId, rows);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Parse comma-separated dataset names into a set; empty set = all. */
    private static Set<String> parseDatasets(String datasetsParam) {
        if (datasetsParam == null || datasetsParam.isBlank()) {
            return Set.of(); // empty = all
        }
        return Arrays.stream(datasetsParam.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    /** Convert a generic Map payload to a typed DTO via Jackson. */
    private <T> T convertPayload(Map<String, Object> payload, Class<T> type) {
        if (payload == null) {
            throw new IllegalArgumentException("Op payload is null");
        }
        try {
            return objectMapper.convertValue(payload, type);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Cannot convert payload to " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private Long payloadLong(SyncOpDto op, String key) {
        Object v = op.payload() == null ? null : op.payload().get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required payload field: " + key);
        }
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Payload field " + key + " is not a number: " + v);
        }
    }

    private BigDecimal payloadDecimal(SyncOpDto op, String key) {
        Object v = op.payload() == null ? null : op.payload().get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing required payload field: " + key);
        }
        if (v instanceof BigDecimal bd) return bd;
        try { return new BigDecimal(v.toString()); }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Payload field " + key + " is not a decimal: " + v);
        }
    }

    private String payloadString(SyncOpDto op, String key) {
        Object v = op.payload() == null ? null : op.payload().get(key);
        return v == null ? null : v.toString();
    }

    /** Lazy accessor for PriceListRepository to avoid circular-dep issues at construction. */
    private com.orbix.engine.modules.catalog.repository.PriceListRepository priceListRepository() {
        return priceListRepo;
    }

    private final com.orbix.engine.modules.catalog.repository.PriceListRepository priceListRepo;

    /** Used to close a session from within the push-batch path.
     *  Calls close() directly on the entity — avoids double cash-ledger entries
     *  that TillSessionService.close would post (cash ledger is handled by the
     *  original TILL_SESSION_OPEN / POS_SALE flows, not repeated here). */
    private com.orbix.engine.modules.pos.domain.dto.TillSessionDto closeSession(
            String uid, BigDecimal declaredCash) {
        TillSession session = sessions.findByUid(uid)
            .orElseThrow(() -> new java.util.NoSuchElementException("Till session not found: " + uid));
        BigDecimal expectedCash = computeExpectedCash(session);
        session.close(expectedCash, declaredCash, context.userId(), null, null);
        sessions.save(session);
        return com.orbix.engine.modules.pos.domain.dto.TillSessionDto.from(session);
    }

    /** Internal result carrier for dataset build methods. */
    private record DatasetResult<T>(List<T> upserts, List<String> deletes, long maxSeq, boolean hasMore) {}
}
