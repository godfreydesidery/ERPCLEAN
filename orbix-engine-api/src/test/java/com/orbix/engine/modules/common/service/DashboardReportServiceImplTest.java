package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.common.domain.dto.DashboardRollupDto;
import com.orbix.engine.modules.procurement.service.LpoOrderService;
import com.orbix.engine.modules.sales.domain.dto.ArSummaryDto;
import com.orbix.engine.modules.sales.domain.dto.DailySummaryDto;
import com.orbix.engine.modules.sales.service.SalesReportService;
import com.orbix.engine.modules.stock.domain.dto.ItemBranchBalanceDto;
import com.orbix.engine.modules.stock.service.StockMoveService;
import com.orbix.engine.modules.stock.service.StockReportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Slice F GAP 7.D — happy path returns all fragments; per-fragment
 * {@link AccessDeniedException} returns {@code null} for that fragment and
 * populates the rest. Pin the per-fragment auth-resolution shape so a future
 * refactor doesn't drop the try/catch wrapper.
 */
@ExtendWith(MockitoExtension.class)
class DashboardReportServiceImplTest {

    private static final Long BRANCH_ID = 12L;
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 5, 28);

    @Mock private SalesReportService salesReportService;
    @Mock private StockReportService stockReportService;
    @Mock private StockMoveService stockMoveService;
    @Mock private LpoOrderService lpoOrderService;

    @InjectMocks private DashboardReportServiceImpl service;

    private ArSummaryDto arSummary() {
        return new ArSummaryDto(new BigDecimal("4250000.0000"), 5L, 12L, "TZS");
    }

    private DailySummaryDto dailySummary() {
        DailySummaryDto.SalesBlock sales = new DailySummaryDto.SalesBlock(
            new BigDecimal("300000"), new BigDecimal("54000"), BigDecimal.ZERO,
            10,
            new BigDecimal("125000"), new BigDecimal("22500"), BigDecimal.ZERO,
            5, 0,
            new BigDecimal("425000.00"));
        DailySummaryDto.PurchasesBlock purchases = new DailySummaryDto.PurchasesBlock(
            BigDecimal.ZERO, BigDecimal.ZERO, 0);
        DailySummaryDto.CashBlock cash = new DailySummaryDto.CashBlock(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new EnumMap<>(CashAccount.class));
        return new DailySummaryDto(BUSINESS_DATE, BRANCH_ID, sales, purchases, cash);
    }

    private List<ItemBranchBalanceDto> negativeOnHand() {
        return List.of(
            new ItemBranchBalanceDto(1L, BRANCH_ID, new BigDecimal("-3"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null, null),
            new ItemBranchBalanceDto(2L, BRANCH_ID, new BigDecimal("-1"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null, null),
            new ItemBranchBalanceDto(3L, BRANCH_ID, new BigDecimal("-5"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null, null, null, null, null));
    }

    private List<ItemBranchBalanceDto> belowReorder() {
        return List.of(
            new ItemBranchBalanceDto(10L, BRANCH_ID, new BigDecimal("5"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null),
            new ItemBranchBalanceDto(11L, BRANCH_ID, new BigDecimal("2"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null),
            new ItemBranchBalanceDto(12L, BRANCH_ID, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null),
            new ItemBranchBalanceDto(13L, BRANCH_ID, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null),
            new ItemBranchBalanceDto(14L, BRANCH_ID, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null),
            new ItemBranchBalanceDto(15L, BRANCH_ID, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null),
            new ItemBranchBalanceDto(16L, BRANCH_ID, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), null, null, null, null, null, null));
    }

    @Test
    void rollup_happyPath_returnsAllFragments() {
        when(salesReportService.arSummary(BRANCH_ID)).thenReturn(arSummary());
        when(salesReportService.dailySummary(BRANCH_ID, BUSINESS_DATE)).thenReturn(dailySummary());
        when(stockReportService.negativeOnHand(BRANCH_ID)).thenReturn(negativeOnHand());
        when(stockMoveService.listBalances(BRANCH_ID, false, true)).thenReturn(belowReorder());
        when(lpoOrderService.countPendingApproval(BRANCH_ID)).thenReturn(2L);

        DashboardRollupDto dto = service.rollup(BRANCH_ID, BUSINESS_DATE);

        assertThat(dto.branchId()).isEqualTo(BRANCH_ID);
        assertThat(dto.businessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(dto.currencyCode()).isEqualTo("TZS");
        assertThat(dto.kpi()).isNotNull();
        assertThat(dto.kpi().todaysSales()).isEqualByComparingTo("425000.00");
        assertThat(dto.kpi().stockAlerts()).isEqualTo(7);
        assertThat(dto.kpi().negativeStockCount()).isEqualTo(3);
        assertThat(dto.kpi().openInvoices()).isEqualTo(12L);
        assertThat(dto.kpi().arOutstanding()).isEqualByComparingTo("4250000.0000");
        assertThat(dto.alerts()).isNotNull();
        assertThat(dto.alerts().stockAlertCount()).isEqualTo(7);
        assertThat(dto.alerts().negativeStockCount()).isEqualTo(3);
        assertThat(dto.alerts().overdueInvoiceCount()).isEqualTo(5L);
        assertThat(dto.alerts().lposPendingApproval()).isEqualTo(2L);
    }

    @Test
    void rollup_nullBusinessDate_defaultsToToday() {
        when(salesReportService.arSummary(BRANCH_ID)).thenReturn(arSummary());
        when(salesReportService.dailySummary(eq(BRANCH_ID), eq(LocalDate.now()))).thenReturn(dailySummary());
        when(stockReportService.negativeOnHand(BRANCH_ID)).thenReturn(negativeOnHand());
        when(stockMoveService.listBalances(BRANCH_ID, false, true)).thenReturn(belowReorder());
        when(lpoOrderService.countPendingApproval(BRANCH_ID)).thenReturn(0L);

        DashboardRollupDto dto = service.rollup(BRANCH_ID, null);

        assertThat(dto.businessDate()).isEqualTo(LocalDate.now());
    }

    @Test
    void rollup_companyWide_skipsStockAlertCount() {
        when(salesReportService.arSummary(null)).thenReturn(arSummary());
        when(salesReportService.dailySummary(null, BUSINESS_DATE)).thenReturn(dailySummary());
        when(stockReportService.negativeOnHand(null)).thenReturn(negativeOnHand());
        when(lpoOrderService.countPendingApproval(null)).thenReturn(1L);

        DashboardRollupDto dto = service.rollup(null, BUSINESS_DATE);

        // Company-wide → stock-alert tile (which is branch-derived) is null.
        assertThat(dto.kpi().stockAlerts()).isNull();
        assertThat(dto.alerts().stockAlertCount()).isNull();
        // Other fragments populated.
        assertThat(dto.kpi().negativeStockCount()).isEqualTo(3);
        assertThat(dto.alerts().lposPendingApproval()).isEqualTo(1L);
    }

    @Test
    void rollup_arDenied_returnsNullArFragment_othersPopulated() {
        when(salesReportService.arSummary(BRANCH_ID))
            .thenThrow(new AccessDeniedException("no SALES.REPORT.AR_SUMMARY"));
        when(salesReportService.dailySummary(BRANCH_ID, BUSINESS_DATE)).thenReturn(dailySummary());
        when(stockReportService.negativeOnHand(BRANCH_ID)).thenReturn(negativeOnHand());
        when(stockMoveService.listBalances(BRANCH_ID, false, true)).thenReturn(belowReorder());
        when(lpoOrderService.countPendingApproval(BRANCH_ID)).thenReturn(2L);

        DashboardRollupDto dto = service.rollup(BRANCH_ID, BUSINESS_DATE);

        // AR-derived sub-fields are null; currency falls back to default.
        assertThat(dto.kpi().arOutstanding()).isNull();
        assertThat(dto.kpi().openInvoices()).isNull();
        assertThat(dto.alerts().overdueInvoiceCount()).isNull();
        assertThat(dto.currencyCode()).isEqualTo("TZS");
        // Other fragments unaffected.
        assertThat(dto.kpi().todaysSales()).isEqualByComparingTo("425000.00");
        assertThat(dto.kpi().negativeStockCount()).isEqualTo(3);
        assertThat(dto.kpi().stockAlerts()).isEqualTo(7);
        assertThat(dto.alerts().lposPendingApproval()).isEqualTo(2L);
    }

    @Test
    void rollup_stockReportDenied_negativeStockCountIsNull() {
        when(salesReportService.arSummary(BRANCH_ID)).thenReturn(arSummary());
        when(salesReportService.dailySummary(BRANCH_ID, BUSINESS_DATE)).thenReturn(dailySummary());
        when(stockReportService.negativeOnHand(BRANCH_ID))
            .thenThrow(new AccessDeniedException("no STOCK.COUNT"));
        when(stockMoveService.listBalances(BRANCH_ID, false, true))
            .thenThrow(new AccessDeniedException("no STOCK.COUNT"));
        when(lpoOrderService.countPendingApproval(BRANCH_ID)).thenReturn(2L);

        DashboardRollupDto dto = service.rollup(BRANCH_ID, BUSINESS_DATE);

        assertThat(dto.kpi().negativeStockCount()).isNull();
        assertThat(dto.alerts().negativeStockCount()).isNull();
        assertThat(dto.kpi().stockAlerts()).isNull();
        assertThat(dto.alerts().stockAlertCount()).isNull();
        // Sales + LPO fragments unaffected.
        assertThat(dto.kpi().arOutstanding()).isEqualByComparingTo("4250000.0000");
        assertThat(dto.alerts().lposPendingApproval()).isEqualTo(2L);
    }

    @Test
    void rollup_lpoDenied_lposPendingIsNull() {
        when(salesReportService.arSummary(BRANCH_ID)).thenReturn(arSummary());
        when(salesReportService.dailySummary(BRANCH_ID, BUSINESS_DATE)).thenReturn(dailySummary());
        when(stockReportService.negativeOnHand(BRANCH_ID)).thenReturn(negativeOnHand());
        when(stockMoveService.listBalances(BRANCH_ID, false, true)).thenReturn(belowReorder());
        when(lpoOrderService.countPendingApproval(BRANCH_ID))
            .thenThrow(new AccessDeniedException("no PROCUREMENT.MANAGE_LPO"));

        DashboardRollupDto dto = service.rollup(BRANCH_ID, BUSINESS_DATE);

        assertThat(dto.alerts().lposPendingApproval()).isNull();
        // Other fragments unaffected.
        assertThat(dto.kpi().arOutstanding()).isEqualByComparingTo("4250000.0000");
        assertThat(dto.kpi().stockAlerts()).isEqualTo(7);
    }

    @Test
    void rollup_dailySummaryDenied_todaysSalesIsNull() {
        when(salesReportService.arSummary(BRANCH_ID)).thenReturn(arSummary());
        when(salesReportService.dailySummary(BRANCH_ID, BUSINESS_DATE))
            .thenThrow(new AccessDeniedException("no sales-summary"));
        when(stockReportService.negativeOnHand(BRANCH_ID)).thenReturn(negativeOnHand());
        when(stockMoveService.listBalances(BRANCH_ID, false, true)).thenReturn(belowReorder());
        when(lpoOrderService.countPendingApproval(BRANCH_ID)).thenReturn(2L);

        DashboardRollupDto dto = service.rollup(BRANCH_ID, BUSINESS_DATE);

        assertThat(dto.kpi().todaysSales()).isNull();
        assertThat(dto.kpi().arOutstanding()).isEqualByComparingTo("4250000.0000");
        assertThat(dto.kpi().stockAlerts()).isEqualTo(7);
    }
}
