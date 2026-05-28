package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the AR-summary wire shape — field names must match the Angular
 * dashboard signals exactly ({@code openInvoices}, {@code arOutstanding},
 * {@code overdueInvoices}) so no mapping layer is needed on the frontend.
 */
class ArSummaryDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void wire_shape_matches_dashboard_signals() throws Exception {
        ArSummaryDto dto = new ArSummaryDto(
            new BigDecimal("4250000.0000"), 3L, 12L, "TZS");

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"arOutstanding\":4250000.0000");
        assertThat(json).contains("\"overdueInvoices\":3");
        assertThat(json).contains("\"openInvoices\":12");
        assertThat(json).contains("\"currencyCode\":\"TZS\"");
    }
}
