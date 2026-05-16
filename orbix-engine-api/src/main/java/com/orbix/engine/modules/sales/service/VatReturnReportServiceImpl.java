package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.dto.VatReturnDto;
import com.orbix.engine.modules.common.domain.dto.VatReturnRowDto;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.pos.domain.enums.PosSaleKind;
import com.orbix.engine.modules.pos.domain.enums.PosSaleStatus;
import com.orbix.engine.modules.pos.repository.PosSaleLineRepository;
import com.orbix.engine.modules.procurement.repository.GrnLineRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceLineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VatReturnReportServiceImpl implements VatReturnReportService {

    private static final int MONEY_SCALE = 4;

    private final RequestContext context;
    private final VatGroupRepository vatGroups;
    private final SalesInvoiceLineRepository salesInvoiceLines;
    private final PosSaleLineRepository posSaleLines;
    private final GrnLineRepository grnLines;

    @Override
    @Transactional(readOnly = true)
    public VatReturnDto vatReturn(Long branchId, LocalDate from, LocalDate to) {
        Long companyId = context.companyId();

        LocalDate today = LocalDate.now();
        LocalDate defaultFrom = today.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        LocalDate defaultTo = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        LocalDate start = from != null ? from : defaultFrom;
        LocalDate end = to != null ? to : defaultTo;

        // Seed every VAT group on the company so a zero-activity group still appears
        // (auditors expect every active rate to show even when no line was booked).
        Map<Long, Bucket> byGroup = new HashMap<>();
        Map<Long, VatGroup> meta = new HashMap<>();
        for (VatGroup g : vatGroups.findByCompanyId(companyId)) {
            meta.put(g.getId(), g);
            byGroup.put(g.getId(), new Bucket(g.getRate()));
        }

        // --- Output (sales) ---
        for (Object[] row : salesInvoiceLines.aggregateOutputVat(companyId, branchId, start, end)) {
            Bucket b = bucketFor(byGroup, meta, (Long) row[0]);
            b.outputNet = b.outputNet.add(asMoney(row[1]));
            b.outputVat = b.outputVat.add(asMoney(row[2]));
        }
        for (Object[] row : posSaleLines.aggregateOutputVat(companyId, branchId, start, end,
                PosSaleStatus.POSTED, PosSaleKind.SALE)) {
            Bucket b = bucketFor(byGroup, meta, (Long) row[0]);
            b.outputNet = b.outputNet.add(asMoney(row[1]));
            b.outputVat = b.outputVat.add(asMoney(row[2]));
        }
        for (Object[] row : posSaleLines.aggregateOutputVat(companyId, branchId, start, end,
                PosSaleStatus.POSTED, PosSaleKind.REFUND)) {
            Bucket b = bucketFor(byGroup, meta, (Long) row[0]);
            // Refund reverses output VAT obligation — subtract from the totals.
            b.outputNet = b.outputNet.subtract(asMoney(row[1]));
            b.outputVat = b.outputVat.subtract(asMoney(row[2]));
        }

        // --- Input (procurement) ---
        for (Object[] row : grnLines.aggregateInputVat(companyId, branchId, start, end)) {
            Long vatGroupId = (Long) row[0];
            BigDecimal rate = asMoney(row[1]);
            BigDecimal net = asMoney(row[2]);
            Bucket b = bucketFor(byGroup, meta, vatGroupId);
            // Compute per-line input VAT at the rate snapshot — matches how the
            // GRN posting service rolls header tax (net × rate, half-up at the
            // money scale). Aggregating by (vat_group_id, rate) means each
            // group has a single rate per query result.
            BigDecimal inputVat = net.multiply(rate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            b.inputNet = b.inputNet.add(net);
            b.inputVat = b.inputVat.add(inputVat);
        }

        // --- Assemble rows ---
        // Preserve a deterministic order: VAT group code asc, then by id.
        Map<Long, Bucket> ordered = new LinkedHashMap<>();
        byGroup.entrySet().stream()
            .sorted(Comparator
                .<Map.Entry<Long, Bucket>, String>comparing(e -> {
                    VatGroup g = meta.get(e.getKey());
                    return g != null ? g.getCode() : "";
                })
                .thenComparing(Map.Entry::getKey))
            .forEach(e -> ordered.put(e.getKey(), e.getValue()));

        List<VatReturnRowDto> rows = new ArrayList<>();
        BigDecimal totalOutputNet = BigDecimal.ZERO;
        BigDecimal totalOutputVat = BigDecimal.ZERO;
        BigDecimal totalInputNet = BigDecimal.ZERO;
        BigDecimal totalInputVat = BigDecimal.ZERO;
        for (Map.Entry<Long, Bucket> e : ordered.entrySet()) {
            VatGroup g = meta.get(e.getKey());
            Bucket b = e.getValue();
            BigDecimal net = b.outputVat.subtract(b.inputVat);
            rows.add(new VatReturnRowDto(
                e.getKey(),
                g != null ? g.getCode() : null,
                g != null ? g.getName() : null,
                b.rate,
                b.outputNet,
                b.outputVat,
                b.inputNet,
                b.inputVat,
                net
            ));
            totalOutputNet = totalOutputNet.add(b.outputNet);
            totalOutputVat = totalOutputVat.add(b.outputVat);
            totalInputNet = totalInputNet.add(b.inputNet);
            totalInputVat = totalInputVat.add(b.inputVat);
        }

        return new VatReturnDto(
            start, end, branchId, rows,
            totalOutputNet, totalOutputVat,
            totalInputNet, totalInputVat,
            totalOutputVat.subtract(totalInputVat)
        );
    }

    /** A bucket for one VAT group while accumulating per-period totals. */
    private static final class Bucket {
        final BigDecimal rate;
        BigDecimal outputNet = BigDecimal.ZERO;
        BigDecimal outputVat = BigDecimal.ZERO;
        BigDecimal inputNet = BigDecimal.ZERO;
        BigDecimal inputVat = BigDecimal.ZERO;

        Bucket(BigDecimal rate) {
            this.rate = rate != null ? rate : BigDecimal.ZERO;
        }
    }

    /**
     * Returns the existing bucket for {@code vatGroupId} or creates one
     * lazily — covers the rare case of an aggregation hit on a VAT group
     * whose row has since been archived (still has historical lines).
     */
    private Bucket bucketFor(Map<Long, Bucket> byGroup, Map<Long, VatGroup> meta, Long vatGroupId) {
        Bucket existing = byGroup.get(vatGroupId);
        if (existing != null) return existing;
        VatGroup orphan = vatGroups.findById(vatGroupId).orElse(null);
        if (orphan != null) meta.put(vatGroupId, orphan);
        Bucket created = new Bucket(orphan != null ? orphan.getRate() : BigDecimal.ZERO);
        byGroup.put(vatGroupId, created);
        return created;
    }

    private static BigDecimal asMoney(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return BigDecimal.ZERO;
    }
}
