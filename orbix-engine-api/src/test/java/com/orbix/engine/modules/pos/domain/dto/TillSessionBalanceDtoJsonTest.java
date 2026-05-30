package com.orbix.engine.modules.pos.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link TillSessionBalanceDto} (ISSUE-CASH-001).
 * {@code sessionId} serialises as a string (JSON:API — Long id fields are
 * strings on the wire). Genuine {@link BigDecimal} balance fields stay numeric.
 */
class TillSessionBalanceDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void sessionId_serialises_as_string_and_amounts_stay_numeric() throws Exception {
        TillSessionBalanceDto dto = new TillSessionBalanceDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            new BigDecimal("50000.0000"),
            new BigDecimal("30000.0000"),
            new BigDecimal("10000.0000"),
            new BigDecimal("2000.0000"),
            new BigDecimal("68000.0000")
        );

        String json = mapper.writeValueAsString(dto);

        // sessionId is a Long named "sessionId" — ends in "Id" → must stringify.
        assertThat(json).contains("\"sessionId\":\"42\"");
        // sessionUid is already a String.
        assertThat(json).contains("\"sessionUid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        // BigDecimal balance fields are genuine numerics — must NOT be quoted.
        assertThat(json).contains("\"openingFloat\":50000.0000");
        assertThat(json).contains("\"cashSales\":30000.0000");
        assertThat(json).contains("\"cashPickups\":10000.0000");
        assertThat(json).contains("\"pettyCash\":2000.0000");
        assertThat(json).contains("\"expectedCash\":68000.0000");
    }
}
