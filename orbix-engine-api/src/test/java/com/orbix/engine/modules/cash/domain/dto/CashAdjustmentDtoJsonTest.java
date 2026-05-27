package com.orbix.engine.modules.cash.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CashAdjustmentDto}. Surrogate-Long PK
 * aggregate with the Slice D reversal lifecycle: {@code reversedAt},
 * {@code reversedBy}, {@code reversedByEntryId} are null while active and
 * populated once archived.
 */
class CashAdjustmentDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void active_adjustment_reversal_columns_null() throws Exception {
        CashAdjustmentDto dto = new CashAdjustmentDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            8123L,
            5L,
            42L,
            LocalDate.of(2026, 5, 27),
            CashAccount.TILL,
            CashDirection.OUT,
            new BigDecimal("250.0000"),
            "TZS",
            "Drawer short — confirmed missing",
            Instant.parse("2026-05-27T06:00:00Z"),
            9L,
            null, null, null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"id\":\"8123\"");
        assertThat(json).contains("\"companyId\":\"5\"");
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"postedBy\":\"9\"");
        assertThat(json).contains("\"direction\":\"OUT\"");
        assertThat(json).contains("\"account\":\"TILL\"");
        assertThat(json).contains("\"amount\":250.0000");
        assertThat(json).contains("\"reversedAt\":null");
        assertThat(json).contains("\"reversedBy\":null");
        assertThat(json).contains("\"reversedByEntryId\":null");
    }

    @Test
    void reversed_adjustment_columns_serialise_as_iso_and_strings() throws Exception {
        CashAdjustmentDto dto = new CashAdjustmentDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            8123L,
            5L,
            42L,
            LocalDate.of(2026, 5, 27),
            CashAccount.TILL,
            CashDirection.OUT,
            new BigDecimal("250.0000"),
            "TZS",
            "Drawer short",
            Instant.parse("2026-05-27T06:00:00Z"),
            9L,
            Instant.parse("2026-05-27T07:30:00Z"),
            11L,
            77L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"reversedAt\":\"2026-05-27T07:30:00Z\"");
        assertThat(json).contains("\"reversedBy\":\"11\"");
        assertThat(json).contains("\"reversedByEntryId\":\"77\"");
    }
}
