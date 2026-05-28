package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerStatementDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void statement_serialisesIdsAsStrings_andNestedRows() throws Exception {
        CustomerStatementDto.OpenInvoiceRow inv = new CustomerStatementDto.OpenInvoiceRow(
            999L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", "INV-001",
            LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 1),
            new BigDecimal("80000.0000"), new BigDecimal("10000.0000"),
            new BigDecimal("70000.0000"), 27, SalesInvoiceStatus.PARTIALLY_PAID
        );
        CustomerStatementDto.RecentReceiptRow rec = new CustomerStatementDto.RecentReceiptRow(
            888L, "01HZ8X7M3K9PJK2D7Q5BCN8W4G", "RCT-001",
            LocalDate.of(2026, 5, 10),
            Instant.parse("2026-05-10T12:30:00Z"),
            new BigDecimal("10000.0000"), "TZS"
        );
        CustomerStatementDto dto = new CustomerStatementDto(
            10042L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4H",
            "Acme Trading",
            "TZS",
            new BigDecimal("500000.0000"),
            new BigDecimal("70000.0000"),
            new BigDecimal("0.1400"),
            1L,
            1L,
            LocalDate.of(2026, 5, 28),
            List.of(inv),
            List.of(rec)
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"customerId\":\"10042\"");
        assertThat(json).contains("\"customerUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4H\"");
        assertThat(json).contains("\"creditLimit\":500000.0000");
        assertThat(json).contains("\"totalOutstanding\":70000.0000");
        assertThat(json).contains("\"creditUtilisation\":0.1400");
        assertThat(json).contains("\"asOf\":\"2026-05-28\"");
        assertThat(json).contains("\"invoiceId\":\"999\"");
        assertThat(json).contains("\"status\":\"PARTIALLY_PAID\"");
        assertThat(json).contains("\"daysOverdue\":27");
        assertThat(json).contains("\"receiptId\":\"888\"");
        assertThat(json).contains("\"postedAt\":\"2026-05-10T12:30:00Z\"");
    }

    @Test
    void statement_nullUtilisation_renders() throws Exception {
        CustomerStatementDto dto = new CustomerStatementDto(
            1L, "01HZ8X7M3K9PJK2D7Q5BCN8W4F", "Zero limit", "TZS",
            BigDecimal.ZERO, BigDecimal.ZERO, null, 0L, 0L,
            LocalDate.of(2026, 5, 28), List.of(), List.of()
        );
        String json = mapper.writeValueAsString(dto);
        assertThat(json).contains("\"creditUtilisation\":null");
    }
}
