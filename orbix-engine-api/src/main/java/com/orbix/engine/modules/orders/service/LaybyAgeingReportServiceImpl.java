package com.orbix.engine.modules.orders.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.orders.domain.dto.LaybyAgeingBucketDto;
import com.orbix.engine.modules.orders.domain.dto.LaybyAgeingOrderDto;
import com.orbix.engine.modules.orders.domain.dto.LaybyAgeingReportDto;
import com.orbix.engine.modules.orders.domain.entity.CustomerOrder;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderStatus;
import com.orbix.engine.modules.orders.domain.enums.CustomerOrderType;
import com.orbix.engine.modules.orders.repository.CustomerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LaybyAgeingReportServiceImpl implements LaybyAgeingReportService {

    private static final List<CustomerOrderStatus> OPEN_STATUSES = List.of(
        CustomerOrderStatus.DRAFT,
        CustomerOrderStatus.RESERVED,
        CustomerOrderStatus.DEPOSIT_PAID,
        CustomerOrderStatus.PARTIALLY_PAID,
        CustomerOrderStatus.READY);

    private static final List<int[]> BUCKETS = List.of(
        new int[]{0, 7},
        new int[]{8, 30},
        new int[]{31, 60},
        new int[]{61, 90},
        new int[]{91, Integer.MAX_VALUE});

    private final CustomerOrderRepository orders;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public LaybyAgeingReportDto report(Long branchId, CustomerOrderType type, Instant asOf) {
        Long companyId = context.companyId();
        Instant cutoff = asOf != null ? asOf : Instant.now();

        List<CustomerOrder> open = branchId != null
            ? orders.findByCompanyIdAndBranchIdAndStatusInOrderByCreatedAtAsc(
                companyId, branchId, OPEN_STATUSES)
            : orders.findByCompanyIdAndStatusInOrderByCreatedAtAsc(companyId, OPEN_STATUSES);

        Map<CustomerOrderType, BigDecimal> balanceByType = new EnumMap<>(CustomerOrderType.class);
        Map<CustomerOrderType, Integer> countByType = new EnumMap<>(CustomerOrderType.class);
        Map<BucketKey, BucketAccumulator> buckets = new java.util.LinkedHashMap<>();
        List<LaybyAgeingOrderDto> drillDown = new ArrayList<>();

        for (CustomerOrder order : open) {
            if (type != null && order.getType() != type) continue;
            int ageDays = (int) Duration.between(order.getCreatedAt(), cutoff).toDays();
            Integer daysUntilExpiry = order.getReservedUntil() == null
                ? null
                : (int) Duration.between(cutoff, order.getReservedUntil()).toDays();
            int[] bucket = bucketFor(ageDays);
            BucketKey key = new BucketKey(order.getType(), bucket[0], bucket[1]);
            BucketAccumulator acc = buckets.computeIfAbsent(key, k -> new BucketAccumulator());
            acc.count++;
            acc.totalAmount = acc.totalAmount.add(order.getTotalAmount());
            acc.paidAmount = acc.paidAmount.add(order.getPaidAmount());
            acc.balanceDue = acc.balanceDue.add(order.getBalanceDue());

            balanceByType.merge(order.getType(), order.getBalanceDue(), BigDecimal::add);
            countByType.merge(order.getType(), 1, Integer::sum);

            drillDown.add(new LaybyAgeingOrderDto(
                order.getId(),
                order.getNumber(),
                order.getBranchId(),
                order.getCustomerId(),
                order.getType(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getReservedUntil(),
                ageDays,
                daysUntilExpiry,
                order.getTotalAmount(),
                order.getPaidAmount(),
                order.getBalanceDue()));
        }

        List<LaybyAgeingBucketDto> bucketRows = new ArrayList<>(buckets.size());
        for (Map.Entry<BucketKey, BucketAccumulator> e : buckets.entrySet()) {
            BucketKey k = e.getKey();
            BucketAccumulator a = e.getValue();
            bucketRows.add(new LaybyAgeingBucketDto(
                k.type, labelFor(k.minDays, k.maxDays),
                k.minDays, k.maxDays,
                a.count, a.totalAmount, a.paidAmount, a.balanceDue));
        }
        bucketRows.sort(Comparator
            .comparing((LaybyAgeingBucketDto b) -> b.type().name())
            .thenComparingInt(LaybyAgeingBucketDto::minDays));
        // Drill-down already oldest-first via the JPQL ORDER BY created_at ASC.

        return new LaybyAgeingReportDto(cutoff, balanceByType, countByType, bucketRows, drillDown);
    }

    private static int[] bucketFor(int ageDays) {
        for (int[] b : BUCKETS) {
            if (ageDays >= b[0] && ageDays <= b[1]) return b;
        }
        return BUCKETS.get(BUCKETS.size() - 1);
    }

    private static String labelFor(int min, int max) {
        if (max == Integer.MAX_VALUE) return min + "+ days";
        return min + "-" + max + " days";
    }

    private record BucketKey(CustomerOrderType type, int minDays, int maxDays) {
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BucketKey b)) return false;
            return minDays == b.minDays && maxDays == b.maxDays && type == b.type;
        }
        @Override public int hashCode() { return Objects.hash(type, minDays, maxDays); }
    }

    private static final class BucketAccumulator {
        int count = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal balanceDue = BigDecimal.ZERO;
    }
}
