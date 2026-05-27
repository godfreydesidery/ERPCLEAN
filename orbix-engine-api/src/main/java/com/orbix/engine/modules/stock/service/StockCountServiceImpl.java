package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.common.service.SettingsService;
import com.orbix.engine.modules.iam.domain.enums.Permissions;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.iam.service.PermissionResolverService;
import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecordCountsRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockCountDto;
import com.orbix.engine.modules.stock.domain.dto.StockMoveDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockCount;
import com.orbix.engine.modules.stock.domain.entity.StockCountLine;
import com.orbix.engine.modules.stock.domain.enums.StockCountStatus;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockCountLineRepository;
import com.orbix.engine.modules.stock.repository.StockCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockCountServiceImpl implements StockCountService {

    static final String APPROVE_PERMISSION = Permissions.STOCK_COUNT_APPROVE;

    private final StockCountRepository counts;
    private final StockCountLineRepository countLines;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
    private final PermissionResolverService permissions;
    private final SettingsService settings;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public List<StockCountDto> listCounts(Long branchId) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<StockCount> rows = scope == null
            ? counts.findByCompanyIdOrderByCountDateDesc(companyId)
            : counts.findByCompanyIdAndBranchIdOrderByCountDateDesc(companyId, scope);
        return rows.stream()
            .map(c -> StockCountDto.from(c, countLines.findByStockCountId(c.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockCountDto getCount(String uid) {
        StockCount count = requireCountByUid(uid);
        return StockCountDto.from(count, countLines.findByStockCountId(count.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "StockCount")
    public StockCountDto createCount(CreateStockCountRequestDto request) {
        Long companyId = context.companyId();
        Long branchId = request.branchId();
        branchScope.requireAccess(branchId);
        String number = request.number().trim().toUpperCase();
        if (counts.existsByBranchIdAndNumber(branchId, number)) {
            throw new IllegalArgumentException("Stock count number already exists: " + number);
        }
        StockCount count = counts.save(new StockCount(number, branchId, companyId,
            request.countDate(), request.type(), context.userId()));
        for (Long itemId : request.itemIds()) {
            BigDecimal systemQty = balances.findById(new ItemBranchBalanceId(itemId, branchId))
                .map(ItemBranchBalance::getQtyOnHand)
                .orElse(BigDecimal.ZERO);
            countLines.save(new StockCountLine(count.getId(), itemId, systemQty));
        }
        List<StockCountLine> lines = countLines.findByStockCountId(count.getId());
        return StockCountDto.from(count, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "START", entityType = "StockCount")
    public StockCountDto startCount(String uid) {
        StockCount count = requireCountByUid(uid);
        count.start();
        List<StockCountLine> lines = countLines.findByStockCountId(count.getId());
        Map<String, Object> payload = new HashMap<>();
        payload.put("stockCountId", count.getId());
        payload.put("uid", count.getUid());
        payload.put("number", count.getNumber());
        payload.put("branchId", count.getBranchId());
        payload.put("lineCount", lines.size());
        payload.put("actorId", context.userId());
        events.publish("StockCountStarted.v1", "StockCount",
            String.valueOf(count.getId()), payload);
        return StockCountDto.from(count, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "RECORD_COUNTS", entityType = "StockCount")
    public StockCountDto recordCounts(String uid, RecordCountsRequestDto request) {
        StockCount count = requireCountByUid(uid);
        if (count.getStatus() != StockCountStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Counts can only be recorded on an IN_PROGRESS count");
        }
        Map<Long, StockCountLine> linesById = countLines.findByStockCountId(count.getId()).stream()
            .collect(Collectors.toMap(StockCountLine::getId, Function.identity()));
        for (RecordCountsRequestDto.CountEntry entry : request.counts()) {
            StockCountLine line = linesById.get(entry.lineId());
            if (line == null) {
                throw new NoSuchElementException("Line not on this count: " + entry.lineId());
            }
            line.recordCount(entry.countedQty(), entry.note());
        }
        return StockCountDto.from(count, countLines.findByStockCountId(count.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = "StockCount")
    public StockCountDto closeCount(String uid) {
        StockCount count = requireCountByUid(uid);
        count.close(context.userId());
        List<StockCountLine> lines = countLines.findByStockCountId(count.getId());
        lines.forEach(StockCountLine::computeVariance);
        BigDecimal varianceValue = computeVarianceValue(count.getBranchId(), lines);
        long varianceCount = lines.stream()
            .map(StockCountLine::getVarianceQty)
            .filter(v -> v != null && v.signum() != 0)
            .count();
        Map<String, Object> payload = new HashMap<>();
        payload.put("stockCountId", count.getId());
        payload.put("uid", count.getUid());
        payload.put("branchId", count.getBranchId());
        payload.put("varianceCount", varianceCount);
        payload.put("varianceValue", varianceValue);
        payload.put("actorId", context.userId());
        events.publish("StockCountClosed.v1", "StockCount",
            String.valueOf(count.getId()), payload);
        return StockCountDto.from(count, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "StockCount")
    public StockCountDto postCount(String uid, PostStockCountRequestDto request) {
        StockCount count = requireCountByUid(uid);
        List<StockCountLine> lines = countLines.findByStockCountId(count.getId());

        BigDecimal varianceValue = computeVarianceValue(count.getBranchId(), lines);
        boolean aboveThreshold = varianceValue.compareTo(
            settings.getDecimal(SettingKey.STOCK_ADJUSTMENT_THRESHOLD)) > 0;
        PostStockCountRequestDto safeRequest = request != null ? request : PostStockCountRequestDto.empty();
        validateAuthoriser(safeRequest.authorisedByUserId(), context.userId(), aboveThreshold);

        count.post();
        List<Long> varianceMoveIds = new ArrayList<>();
        List<Map<String, Object>> adjustmentLines = new ArrayList<>();
        for (StockCountLine line : lines) {
            BigDecimal variance = line.getVarianceQty();
            if (variance == null || variance.signum() == 0) {
                continue;
            }
            BigDecimal unitCost = balances
                .findById(new ItemBranchBalanceId(line.getItemId(), count.getBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            StockMoveDto move = stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), count.getBranchId(), variance, unitCost,
                StockMoveType.ADJUSTMENT, "StockCount", count.getId(), line.getNote(), true));
            varianceMoveIds.add(move.id());
            Map<String, Object> entry = new HashMap<>();
            entry.put("itemId", line.getItemId());
            entry.put("variance", variance);
            entry.put("cost", unitCost);
            entry.put("stockMoveId", move.id());
            adjustmentLines.add(entry);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("stockCountId", count.getId());
        payload.put("uid", count.getUid());
        payload.put("branchId", count.getBranchId());
        payload.put("varianceValue", varianceValue);
        payload.put("aboveThreshold", aboveThreshold);
        payload.put("authorisedByUserId", safeRequest.authorisedByUserId());
        payload.put("actorId", context.userId());
        payload.put("varianceStockMoveIds", varianceMoveIds);
        payload.put("adjustmentLines", adjustmentLines);
        events.publish("StockCountPosted.v1", "StockCount",
            String.valueOf(count.getId()), payload);

        return StockCountDto.from(count, lines);
    }

    /**
     * Signed absolute variance value at the branch's current moving-average cost.
     * Mirrors {@link AdjustmentServiceImpl#postAdjustment}'s monetary impact
     * but aggregated across every non-zero variance line on the count.
     */
    private BigDecimal computeVarianceValue(Long branchId, List<StockCountLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (StockCountLine line : lines) {
            BigDecimal v = line.getVarianceQty();
            if (v == null || v.signum() == 0) continue;
            BigDecimal cost = balances.findById(new ItemBranchBalanceId(line.getItemId(), branchId))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            total = total.add(v.abs().multiply(cost));
        }
        return total;
    }

    private void validateAuthoriser(Long authoriserId, Long actorId, boolean required) {
        if (!required) {
            return;
        }
        if (authoriserId == null) {
            throw new IllegalArgumentException(
                "An authoriser holding " + APPROVE_PERMISSION
                    + " is required to post an above-threshold stock count");
        }
        if (Objects.equals(authoriserId, actorId)) {
            throw new IllegalArgumentException("You cannot authorise your own stock count");
        }
        boolean canApprove = permissions.resolve(authoriserId, context.companyId(), null)
            .contains(APPROVE_PERMISSION);
        if (!canApprove) {
            throw new AccessDeniedException(
                "Authoriser " + authoriserId + " does not hold " + APPROVE_PERMISSION);
        }
    }

    private StockCount requireCountByUid(String uid) {
        StockCount count = counts.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Stock count not found: " + uid));
        if (!Objects.equals(count.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Stock count not found: " + uid);
        }
        branchScope.requireAccess(count.getBranchId());
        return count;
    }
}
