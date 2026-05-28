package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link DebtAgingDto}. Long ids stringify
 * globally via {@code IdLongAsStringSerializerModifier}; decimals stay
 * numeric.
 */
class DebtAgingDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void agingReport_serialisesIdsAsStrings_andDecimalsNumeric() throws Exception {
        DebtAgingDto.CustomerRow row = new DebtAgingDto.CustomerRow(
            10042L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "Acme Trading",
            new BigDecimal("200000.0000"),
            new BigDecimal("100000.0000"),
            new BigDecimal("50000.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("350000.0000"),
            45,
            new BigDecimal("1000000.0000"),
            new BigDecimal("0.3500")
        );
        DebtAgingDto.Totals totals = new DebtAgingDto.Totals(
            new BigDecimal("200000.0000"),
            new BigDecimal("100000.0000"),
            new BigDecimal("50000.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("350000.0000"),
            1L
        );
        DebtAgingDto dto = new DebtAgingDto(
            LocalDate.of(2026, 5, 28),
            42L,
            "TZS",
            totals,
            List.of(row)
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"asOf\":\"2026-05-28\"");
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"currencyCode\":\"TZS\"");
        assertThat(json).contains("\"customerId\":\"10042\"");
        assertThat(json).contains("\"customerUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"customerName\":\"Acme Trading\"");
        assertThat(json).contains("\"current\":200000.0000");
        assertThat(json).contains("\"d1_30\":100000.0000");
        assertThat(json).contains("\"d31_60\":50000.0000");
        assertThat(json).contains("\"totalOutstanding\":350000.0000");
        assertThat(json).contains("\"oldestDaysOverdue\":45");
        assertThat(json).contains("\"creditLimit\":1000000.0000");
        assertThat(json).contains("\"creditUtilisation\":0.3500");
        assertThat(json).contains("\"customerCount\":1");
    }

    @Test
    void rowWithNullUtilisation_serialisesAsJsonNull() throws Exception {
        DebtAgingDto.CustomerRow row = new DebtAgingDto.CustomerRow(
            7L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", "Zero limit", BigDecimal.ZERO,
            new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("100"), 10, BigDecimal.ZERO, null
        );
        String json = mapper.writeValueAsString(row);
        assertThat(json).contains("\"creditUtilisation\":null");
        assertThat(json).contains("\"customerId\":\"7\"");
    }
}
