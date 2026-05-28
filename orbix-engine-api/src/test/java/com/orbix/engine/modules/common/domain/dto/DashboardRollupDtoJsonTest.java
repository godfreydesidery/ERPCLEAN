package com.orbix.engine.modules.common.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice F GAP 7.E — pin the wire shape of {@link DashboardRollupDto}:
 * <ul>
 *   <li>{@code branchId} Long stringifies (per {@code IdLongAsStringSerializerModifier});</li>
 *   <li>BigDecimal money fields stay numeric;</li>
 *   <li>counts stay numeric;</li>
 *   <li>null fragments render as JSON {@code null}.</li>
 * </ul>
 */
class DashboardRollupDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void all_sections_present_branchId_stringifies_numerics_stay_numeric() throws Exception {
        DashboardRollupDto.KpiSection kpi = new DashboardRollupDto.KpiSection(
            new BigDecimal("425000.00"),
            7,
            3,
            12L,
            new BigDecimal("4250000.0000")
        );
        DashboardRollupDto.AlertSection alerts = new DashboardRollupDto.AlertSection(
            7, 3, 5L, 2L
        );
        DashboardRollupDto dto = new DashboardRollupDto(
            1L, LocalDate.of(2026, 5, 28), "TZS", kpi, alerts);

        String json = mapper.writeValueAsString(dto);

        // branchId Long stringifies (ends-in-Id rule).
        assertThat(json).contains("\"branchId\":\"1\"");
        // Plain date.
        assertThat(json).contains("\"businessDate\":\"2026-05-28\"");
        assertThat(json).contains("\"currencyCode\":\"TZS\"");
        // BigDecimal money stays numeric.
        assertThat(json).contains("\"todaysSales\":425000.00");
        assertThat(json).contains("\"arOutstanding\":4250000.0000");
        // Counts stay numeric.
        assertThat(json).contains("\"stockAlerts\":7");
        assertThat(json).contains("\"negativeStockCount\":3");
        assertThat(json).contains("\"openInvoices\":12");
        assertThat(json).contains("\"stockAlertCount\":7");
        assertThat(json).contains("\"overdueInvoiceCount\":5");
        assertThat(json).contains("\"lposPendingApproval\":2");
    }

    @Test
    void null_fragments_render_as_json_null() throws Exception {
        DashboardRollupDto dto = new DashboardRollupDto(
            null, LocalDate.of(2026, 5, 28), "TZS", null, null);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"branchId\":null");
        assertThat(json).contains("\"kpi\":null");
        assertThat(json).contains("\"alerts\":null");
        assertThat(json).contains("\"currencyCode\":\"TZS\"");
    }

    @Test
    void null_subfields_within_sections_render_as_json_null() throws Exception {
        DashboardRollupDto.KpiSection kpi = new DashboardRollupDto.KpiSection(
            null, null, null, null, null);
        DashboardRollupDto.AlertSection alerts = new DashboardRollupDto.AlertSection(
            null, null, null, null);
        DashboardRollupDto dto = new DashboardRollupDto(
            2L, LocalDate.of(2026, 5, 28), "TZS", kpi, alerts);

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"todaysSales\":null");
        assertThat(json).contains("\"arOutstanding\":null");
        assertThat(json).contains("\"openInvoices\":null");
        assertThat(json).contains("\"lposPendingApproval\":null");
    }
}
