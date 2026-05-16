package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.admin.domain.entity.Section;
import com.orbix.engine.modules.admin.repository.SectionRepository;
import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.production.domain.dto.WastageReportRowDto;
import com.orbix.engine.modules.production.domain.enums.WastageCategory;
import com.orbix.engine.modules.production.repository.ProductionWastageRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WastageReportServiceImpl implements WastageReportService {

    private final ProductionWastageRepository wastage;
    private final SectionRepository sections;
    private final ItemRepository items;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<WastageReportRowDto> report(Long branchId, Long sectionId,
                                            WastageCategory category,
                                            LocalDate from, LocalDate to) {
        Long companyId = context.companyId();
        LocalDate start = from != null ? from : LocalDate.now().minusDays(30);
        LocalDate end = to != null ? to : LocalDate.now();
        Instant fromInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<BucketKey, Aggregate> buckets = new HashMap<>();
        for (Object[] row : wastage.aggregateBySectionCategoryItem(
                companyId, branchId, sectionId, category, fromInstant, toInstant)) {
            Long secId = ((Number) row[0]).longValue();
            WastageCategory cat = (WastageCategory) row[1];
            Long itemId = ((Number) row[2]).longValue();
            BigDecimal qty = bd(row[3]);
            int count = ((Number) row[4]).intValue();

            BigDecimal avgCost = items.findById(itemId)
                .map(Item::getAvgCost)
                .orElse(BigDecimal.ZERO);
            Aggregate agg = buckets.computeIfAbsent(new BucketKey(secId, cat), k -> new Aggregate());
            agg.totalQty = agg.totalQty.add(qty);
            agg.totalCost = agg.totalCost.add(qty.multiply(avgCost));
            agg.distinctItems.add(itemId);
            agg.recordCount += count;
        }

        List<WastageReportRowDto> out = new ArrayList<>(buckets.size());
        for (Map.Entry<BucketKey, Aggregate> e : buckets.entrySet()) {
            Section section = sections.findById(e.getKey().sectionId).orElse(null);
            if (section == null) continue;
            Aggregate a = e.getValue();
            out.add(new WastageReportRowDto(
                section.getId(),
                section.getCode(),
                section.getName(),
                section.getBranchId(),
                e.getKey().category,
                a.totalQty,
                a.totalCost,
                a.distinctItems.size(),
                a.recordCount));
        }
        // Sort: section name asc, then total cost desc inside the section so
        // the highest-loss category surfaces first per section.
        out.sort(Comparator.comparing(WastageReportRowDto::sectionName,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(WastageReportRowDto::totalCost, Comparator.reverseOrder()));
        return out;
    }

    private static BigDecimal bd(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }

    private record BucketKey(Long sectionId, WastageCategory category) {
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BucketKey b)) return false;
            return Objects.equals(sectionId, b.sectionId) && category == b.category;
        }
        @Override public int hashCode() { return Objects.hash(sectionId, category); }
    }

    private static final class Aggregate {
        BigDecimal totalQty = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        Set<Long> distinctItems = new HashSet<>();
        int recordCount = 0;
    }
}
