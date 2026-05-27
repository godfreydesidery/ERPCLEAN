package com.orbix.engine.modules.cash.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link BankDepositDto}. Slice D reversal
 * lifecycle: the two compensating-entry ids surface separately so the UI
 * can link directly to each reversing ledger row.
 */
class BankDepositDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void active_deposit_reversal_columns_null() throws Exception {
        BankDepositDto dto = new BankDepositDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            9123L,
            5L,
            42L,
            LocalDate.of(2026, 5, 27),
            new BigDecimal("500000.0000"),
            "TZS",
            "BANK-SLIP-2026-05-27-001",
            "End of day",
            Instant.parse("2026-05-27T18:00:00Z"),
            9L,
            null, null, null, null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"id\":\"9123\"");
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"postedBy\":\"9\"");
        assertThat(json).contains("\"amount\":500000.0000");
        assertThat(json).contains("\"reference\":\"BANK-SLIP-2026-05-27-001\"");
        assertThat(json).contains("\"reversedAt\":null");
        assertThat(json).contains("\"reversedByOutEntryId\":null");
        assertThat(json).contains("\"reversedByInEntryId\":null");
    }

    @Test
    void reversed_deposit_columns_serialise_as_iso_and_strings() throws Exception {
        BankDepositDto dto = new BankDepositDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            9123L,
            5L,
            42L,
            LocalDate.of(2026, 5, 27),
            new BigDecimal("500000.0000"),
            "TZS",
            "BANK-SLIP-2026-05-27-001",
            "End of day",
            Instant.parse("2026-05-27T18:00:00Z"),
            9L,
            Instant.parse("2026-05-27T19:30:00Z"),
            11L,
            101L,
            102L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"reversedAt\":\"2026-05-27T19:30:00Z\"");
        assertThat(json).contains("\"reversedBy\":\"11\"");
        assertThat(json).contains("\"reversedByOutEntryId\":\"101\"");
        assertThat(json).contains("\"reversedByInEntryId\":\"102\"");
    }
}
