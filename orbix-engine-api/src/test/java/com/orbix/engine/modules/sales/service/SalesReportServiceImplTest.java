package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.cash.repository.CashBookRepository;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.pos.repository.PosSaleRepository;
import com.orbix.engine.modules.pos.repository.TillSessionRepository;
import com.orbix.engine.modules.pos.service.TillReportService;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.sales.domain.dto.ArSummaryDto;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Slice C — AR-summary dashboard tile feed.
 */
@ExtendWith(MockitoExtension.class)
class SalesReportServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long BRANCH_ID = 12L;

    @Mock private SalesInvoiceRepository invoices;
    @Mock private PosSaleRepository posSales;
    @Mock private GrnRepository grns;
    @Mock private CashBookRepository cashBooks;
    @Mock private TillSessionRepository tillSessions;
    @Mock private TillReportService tillReports;
    @Mock private RequestContext context;
    @Mock private BranchScope branchScope;

    @InjectMocks private SalesReportServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
    }

    @Test
    void arSummary_pinsWireShape_andDelegatesToRepoQueries() {
        when(branchScope.requireReadable(BRANCH_ID)).thenReturn(BRANCH_ID);
        when(invoices.sumOutstandingForBranch(COMPANY_ID, BRANCH_ID))
            .thenReturn(new BigDecimal("4250000.0000"));
        when(invoices.countOpenForBranch(COMPANY_ID, BRANCH_ID)).thenReturn(12L);
        when(invoices.countOverdueForBranch(eq(COMPANY_ID), eq(BRANCH_ID), any(LocalDate.class)))
            .thenReturn(3L);

        ArSummaryDto dto = service.arSummary(BRANCH_ID);

        assertThat(dto.arOutstanding()).isEqualByComparingTo("4250000");
        assertThat(dto.overdueInvoices()).isEqualTo(3L);
        assertThat(dto.openInvoices()).isEqualTo(12L);
        assertThat(dto.currencyCode()).isEqualTo("TZS");
    }

    @Test
    void arSummary_companyWide_passesNullBranchToRepo() {
        when(branchScope.requireReadable(null)).thenReturn(null);
        when(invoices.sumOutstandingForBranch(COMPANY_ID, null)).thenReturn(BigDecimal.ZERO);
        when(invoices.countOpenForBranch(COMPANY_ID, null)).thenReturn(0L);
        when(invoices.countOverdueForBranch(eq(COMPANY_ID), eq(null), any(LocalDate.class)))
            .thenReturn(0L);

        ArSummaryDto dto = service.arSummary(null);

        assertThat(dto.arOutstanding()).isEqualByComparingTo("0");
        assertThat(dto.openInvoices()).isZero();
        assertThat(dto.overdueInvoices()).isZero();
    }
}
