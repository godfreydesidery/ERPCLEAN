package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.CreateStockCountRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.RecordCountsRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockCountDto;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockCountServiceImpl implements StockCountService {

    private final StockCountRepository counts;
    private final StockCountLineRepository countLines;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
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
    public StockCountDto getCount(Long countId) {
        StockCount count = requireCount(countId);
        return StockCountDto.from(count, countLines.findByStockCountId(countId));
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
        return StockCountDto.from(count, countLines.findByStockCountId(count.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "START", entityType = "StockCount")
    public StockCountDto startCount(Long countId) {
        StockCount count = requireCount(countId);
        count.start();
        return StockCountDto.from(count, countLines.findByStockCountId(countId));
    }

    @Override
    @Transactional
    @Auditable(action = "RECORD_COUNTS", entityType = "StockCount")
    public StockCountDto recordCounts(Long countId, RecordCountsRequestDto request) {
        StockCount count = requireCount(countId);
        if (count.getStatus() != StockCountStatus.IN_PROGRESS) {
            throw new IllegalArgumentException("Counts can only be recorded on an IN_PROGRESS count");
        }
        Map<Long, StockCountLine> linesById = countLines.findByStockCountId(countId).stream()
            .collect(Collectors.toMap(StockCountLine::getId, Function.identity()));
        for (RecordCountsRequestDto.CountEntry entry : request.counts()) {
            StockCountLine line = linesById.get(entry.lineId());
            if (line == null) {
                throw new NoSuchElementException("Line not on this count: " + entry.lineId());
            }
            line.recordCount(entry.countedQty(), entry.note());
        }
        return StockCountDto.from(count, countLines.findByStockCountId(countId));
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = "StockCount")
    public StockCountDto closeCount(Long countId) {
        StockCount count = requireCount(countId);
        count.close(context.userId());
        List<StockCountLine> lines = countLines.findByStockCountId(countId);
        lines.forEach(StockCountLine::computeVariance);
        return StockCountDto.from(count, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = "StockCount")
    public StockCountDto postCount(Long countId) {
        StockCount count = requireCount(countId);
        count.post();
        List<StockCountLine> lines = countLines.findByStockCountId(countId);
        for (StockCountLine line : lines) {
            BigDecimal variance = line.getVarianceQty();
            if (variance == null || variance.signum() == 0) {
                continue;
            }
            BigDecimal unitCost = balances
                .findById(new ItemBranchBalanceId(line.getItemId(), count.getBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), count.getBranchId(), variance, unitCost,
                StockMoveType.ADJUSTMENT, "StockCount", countId, line.getNote(), true));
        }
        return StockCountDto.from(count, lines);
    }

    private StockCount requireCount(Long countId) {
        StockCount count = counts.findById(countId)
            .orElseThrow(() -> new NoSuchElementException("Stock count not found: " + countId));
        if (!Objects.equals(count.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Stock count not found: " + countId);
        }
        branchScope.requireAccess(count.getBranchId());
        return count;
    }
}
