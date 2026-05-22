package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.production.repository.ProductionWastageRepository;
import com.orbix.engine.modules.sales.domain.dto.SectionPnlRowDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SectionPnlReportServiceImpl implements SectionPnlReportService {

    private final PosSaleLineRepository posLines;
    private final ProductionWastageRepository wastage;
    private final SectionRepository sections;
    private final ItemRepository items;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public List<SectionPnlRowDto> report(Long branchId, LocalDate from, LocalDate to) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();

        Map<Long, Aggregate> aggBySection = new HashMap<>();

        // 1) POS revenue + COGS per section (POSTED, kind = SALE).
        for (Object[] row : posLines.aggregateBySection(companyId, scope, start, end,
                PosSaleStatus.POSTED, PosSaleKind.SALE)) {
            Long sectionId = ((Number) row[0]).longValue();
            BigDecimal revenue = bd(row[1]);
            BigDecimal cogs = bd(row[2]);
            int count = ((Number) row[3]).intValue();
            Aggregate agg = aggBySection.computeIfAbsent(sectionId, k -> new Aggregate());
            agg.revenue = agg.revenue.add(revenue);
            agg.cogs = agg.cogs.add(cogs);
            agg.saleCount += count;
        }

        // 2) POS refunds per section (POSTED, kind = REFUND) — subtract from revenue.
        for (Object[] row : posLines.aggregateBySection(companyId, scope, start, end,
                PosSaleStatus.POSTED, PosSaleKind.REFUND)) {
            Long sectionId = ((Number) row[0]).longValue();
            BigDecimal refundAmount = bd(row[1]);
            BigDecimal refundCogs = bd(row[2]);
            int count = ((Number) row[3]).intValue();
            Aggregate agg = aggBySection.computeIfAbsent(sectionId, k -> new Aggregate());
            agg.refunds = agg.refunds.add(refundAmount);
            // Refund returns stock to shelf; COGS is reversed too.
            agg.cogs = agg.cogs.subtract(refundCogs);
            agg.refundCount += count;
        }

        // 3) Production wastage per section — qty + best-effort cost from item.avg_cost.
        Instant fromInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        for (Object[] row : wastage.aggregateBySectionAndItem(companyId, scope,
                fromInstant, toInstant)) {
            Long sectionId = ((Number) row[0]).longValue();
            Long itemId = ((Number) row[1]).longValue();
            BigDecimal qty = bd(row[2]);
            Aggregate agg = aggBySection.computeIfAbsent(sectionId, k -> new Aggregate());
            agg.wastageQty = agg.wastageQty.add(qty);
            BigDecimal avgCost = items.findById(itemId)
                .map(Item::getAvgCost)
                .orElse(BigDecimal.ZERO);
            agg.wastageCost = agg.wastageCost.add(qty.multiply(avgCost));
        }

        // Materialise rows + sort by revenue descending for the dashboard.
        List<SectionPnlRowDto> out = new ArrayList<>(aggBySection.size());
        for (Map.Entry<Long, Aggregate> e : aggBySection.entrySet()) {
            Section section = sections.findById(e.getKey()).orElse(null);
            if (section == null) continue;
            Aggregate a = e.getValue();
            BigDecimal netRevenue = a.revenue.subtract(a.refunds);
            BigDecimal grossMargin = netRevenue.subtract(a.cogs);
            out.add(new SectionPnlRowDto(
                section.getId(),
                section.getCode(),
                section.getName(),
                section.getBranchId(),
                a.revenue,
                a.refunds,
                a.cogs,
                grossMargin,
                a.wastageQty,
                a.wastageCost,
                a.saleCount,
                a.refundCount));
        }
        out.sort(Comparator.comparing(SectionPnlRowDto::revenue).reversed());
        return out;
    }

    private static BigDecimal bd(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private static final class Aggregate {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refunds = BigDecimal.ZERO;
        BigDecimal cogs = BigDecimal.ZERO;
        BigDecimal wastageQty = BigDecimal.ZERO;
        BigDecimal wastageCost = BigDecimal.ZERO;
        int saleCount = 0;
        int refundCount = 0;
    }
}
