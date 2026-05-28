package com.orbix.engine.modules.procurement.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link SupplierStatementDto}. Long ids stringify
 * globally; decimals stay numeric.
 */
class SupplierStatementDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void statement_serialisesIdsAsStrings_andDecimalsNumeric() throws Exception {
        SupplierAgingDto.SupplierRow agingRow = new SupplierAgingDto.SupplierRow(
            40055L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4J",
            "Precision Tools Ltd",
            BigDecimal.ZERO,
            new BigDecimal("55000.0000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("55000.0000"),
            20
        );
        SupplierStatementDto.OpenInvoiceRow inv = new SupplierStatementDto.OpenInvoiceRow(
            7001L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4K",
            "SINV-001",
            "EXT-100",
            LocalDate.of(2026, 4, 1),
            LocalDate.of(2026, 5, 8),
            new BigDecimal("55000.0000"),
            BigDecimal.ZERO,
            new BigDecimal("55000.0000"),
            20,
            SupplierInvoiceStatus.POSTED
        );
        SupplierStatementDto dto = new SupplierStatementDto(
            40055L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4J",
            "Precision Tools Ltd",
            "TZS",
            new BigDecimal("55000.0000"),
            1L,
            1L,
            LocalDate.of(2026, 5, 28),
            agingRow,
            List.of(inv),
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"supplierId\":\"40055\"");
        assertThat(json).contains("\"supplierUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4J\"");
        assertThat(json).contains("\"supplierName\":\"Precision Tools Ltd\"");
        assertThat(json).contains("\"totalOutstanding\":55000.0000");
        assertThat(json).contains("\"openInvoiceCount\":1");
        assertThat(json).contains("\"overdueInvoiceCount\":1");
        assertThat(json).contains("\"asOf\":\"2026-05-28\"");
        assertThat(json).contains("\"invoiceId\":\"7001\"");
        assertThat(json).contains("\"number\":\"SINV-001\"");
        assertThat(json).contains("\"status\":\"POSTED\"");
        assertThat(json).contains("\"recentPayments\":[]");
    }
}
