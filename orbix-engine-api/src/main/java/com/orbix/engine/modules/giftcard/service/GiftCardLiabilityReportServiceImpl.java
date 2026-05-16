package com.orbix.engine.modules.giftcard.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardLiabilityReportDto;
import com.orbix.engine.modules.giftcard.domain.dto.GiftCardLiabilityRowDto;
import com.orbix.engine.modules.giftcard.domain.enums.GiftCardStatus;
import com.orbix.engine.modules.giftcard.repository.GiftCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GiftCardLiabilityReportServiceImpl implements GiftCardLiabilityReportService {

    /** Statuses that count as money we still owe the bearer. */
    private static final Set<GiftCardStatus> OUTSTANDING =
        Set.of(GiftCardStatus.ACTIVE, GiftCardStatus.FROZEN);

    private final GiftCardRepository giftCards;
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public GiftCardLiabilityReportDto report(Long branchId, Instant asOf) {
        Long companyId = context.companyId();
        List<Object[]> raw = giftCards.aggregateLiability(companyId, branchId, asOf);

        List<GiftCardLiabilityRowDto> rows = new ArrayList<>(raw.size());
        Map<String, BigDecimal> outstandingByCurrency = new LinkedHashMap<>();
        Map<String, Integer> outstandingCountByCurrency = new LinkedHashMap<>();
        for (Object[] row : raw) {
            GiftCardStatus status = (GiftCardStatus) row[0];
            String currency = (String) row[1];
            Long branchOfRow = ((Number) row[2]).longValue();
            BigDecimal balance = bd(row[3]);
            int count = ((Number) row[4]).intValue();
            boolean outstanding = OUTSTANDING.contains(status);
            rows.add(new GiftCardLiabilityRowDto(
                status, currency, branchOfRow, balance, count, outstanding));
            if (outstanding) {
                outstandingByCurrency.merge(currency, balance, BigDecimal::add);
                outstandingCountByCurrency.merge(currency, count, Integer::sum);
            }
        }
        // Sort rows: outstanding first, then by currency / status for
        // readability in the dashboard table.
        rows.sort(Comparator
            .comparing(GiftCardLiabilityRowDto::outstandingLiability, Comparator.reverseOrder())
            .thenComparing(GiftCardLiabilityRowDto::currencyCode,
                Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(r -> r.status().name()));
        return new GiftCardLiabilityReportDto(asOf, outstandingByCurrency,
            outstandingCountByCurrency, rows);
    }

    private static BigDecimal bd(Object value) {
        if (value instanceof BigDecimal b) return b;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
