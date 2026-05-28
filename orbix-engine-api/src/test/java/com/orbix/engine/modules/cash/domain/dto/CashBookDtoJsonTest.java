package com.orbix.engine.modules.cash.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CashBookDto}. Composite-PK aggregate
 * (ADR 0002 Path A): the four composite components stay on the wire along
 * with {@code uid}; {@code branchId} stringifies under the global Long-id
 * modifier; money fields stay numeric.
 */
class CashBookDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void uid_and_id_fields_serialise_as_strings_amounts_stay_numeric() throws Exception {
        CashBookDto dto = new CashBookDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            42L,
            CashAccount.TILL,
            LocalDate.of(2026, 5, 27),
            "TZS",
            new BigDecimal("50000.0000"),
            new BigDecimal("12000.0000"),
            new BigDecimal("3500.0000"),
            new BigDecimal("58500.0000")
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"account\":\"TILL\"");
        assertThat(json).contains("\"businessDate\":\"2026-05-27\"");
        assertThat(json).contains("\"currencyCode\":\"TZS\"");
        // Money fields must stay numeric.
        assertThat(json).contains("\"openingAmount\":50000.0000");
        assertThat(json).contains("\"closingAmount\":58500.0000");
        // No surrogate id on the composite-PK aggregate.
        assertThat(json).doesNotContain("\"id\":");
    }
}
