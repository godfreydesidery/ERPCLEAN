package com.orbix.engine.modules.procurement.domain.dto;

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
 * Pin the JSON wire shape of {@link SupplierAgingDto}. Long ids stringify
 * globally via {@code IdLongAsStringSerializerModifier}; decimals stay numeric.
 */
class SupplierAgingDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void agingReport_serialisesIdsAsStrings_andDecimalsNumeric() throws Exception {
        SupplierAgingDto.SupplierRow row = new SupplierAgingDto.SupplierRow(
            20099L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "Global Supplies Ltd",
            new BigDecimal("150000.0000"),
            new BigDecimal("80000.0000"),
            new BigDecimal("40000.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("270000.0000"),
            45
        );
        SupplierAgingDto.Totals totals = new SupplierAgingDto.Totals(
            new BigDecimal("150000.0000"),
            new BigDecimal("80000.0000"),
            new BigDecimal("40000.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("270000.0000"),
            1L
        );
        SupplierAgingDto dto = new SupplierAgingDto(
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
        assertThat(json).contains("\"supplierId\":\"20099\"");
        assertThat(json).contains("\"supplierUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"supplierName\":\"Global Supplies Ltd\"");
        assertThat(json).contains("\"current\":150000.0000");
        assertThat(json).contains("\"d1_30\":80000.0000");
        assertThat(json).contains("\"d31_60\":40000.0000");
        assertThat(json).contains("\"totalOutstanding\":270000.0000");
        assertThat(json).contains("\"oldestDaysOverdue\":45");
        assertThat(json).contains("\"supplierCount\":1");
    }
}
