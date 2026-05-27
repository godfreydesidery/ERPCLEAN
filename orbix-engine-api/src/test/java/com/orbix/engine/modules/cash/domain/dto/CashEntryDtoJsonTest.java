package com.orbix.engine.modules.cash.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.cash.domain.enums.CashAccount;
import com.orbix.engine.modules.cash.domain.enums.CashDirection;
import com.orbix.engine.modules.cash.domain.enums.GlCategory;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CashEntryDto}. Surrogate-Long PK
 * aggregate with the immutable / append-only invariant — uid + id both
 * surface as strings, money fields stay numeric.
 */
class CashEntryDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void uid_and_id_fields_serialise_as_strings_amounts_stay_numeric() throws Exception {
        CashEntryDto dto = new CashEntryDto(
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            7000L,
            Instant.parse("2026-05-27T06:00:00Z"),
            5L,
            42L,
            LocalDate.of(2026, 5, 27),
            CashAccount.TILL,
            CashDirection.IN,
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000"),
            BigDecimal.ONE,
            "TZS",
            "PosSalePayment",
            1L,
            GlCategory.CASH,
            "POS sale",
            9L
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"id\":\"7000\"");
        assertThat(json).contains("\"companyId\":\"5\"");
        assertThat(json).contains("\"branchId\":\"42\"");
        assertThat(json).contains("\"refId\":\"1\"");
        assertThat(json).contains("\"actorId\":\"9\"");
        assertThat(json).contains("\"direction\":\"IN\"");
        assertThat(json).contains("\"account\":\"TILL\"");
        assertThat(json).contains("\"businessDate\":\"2026-05-27\"");
        // Money + FX stay numeric.
        assertThat(json).contains("\"amount\":100.0000");
        assertThat(json).contains("\"tenderAmount\":100.0000");
    }
}
