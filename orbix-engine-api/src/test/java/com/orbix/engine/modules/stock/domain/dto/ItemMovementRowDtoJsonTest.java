package com.orbix.engine.modules.stock.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link ItemMovementRowDto}: {@code itemId} stringifies;
 * genuine numerics stay numeric; {@code lastMoveAt} renders as ISO-8601;
 * {@code moveCount} is a plain integer on the wire.
 */
class ItemMovementRowDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()))
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void wire_shape_itemId_stringified_numerics_numeric_instant_iso() throws Exception {
        Instant lastMove = Instant.parse("2026-04-30T15:00:00Z");
        ItemMovementRowDto dto = new ItemMovementRowDto(
            8801L, "COKE-500", "Coca-Cola 500ml",
            new BigDecimal("200.0000"), new BigDecimal("48.0000"),
            7L, lastMove
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json)
            .contains("\"itemId\":\"8801\"")
            .contains("\"itemCode\":\"COKE-500\"")
            .contains("\"itemName\":\"Coca-Cola 500ml\"")
            .contains("\"movedQty\":200.0000")
            .contains("\"qtyOnHand\":48.0000")
            .contains("\"moveCount\":7")
            .contains("\"lastMoveAt\":\"2026-04-30T15:00:00Z\"");
    }

    @Test
    void null_lastMoveAt_renders_as_null() throws Exception {
        ItemMovementRowDto dto = new ItemMovementRowDto(
            1L, "X", "Item X",
            BigDecimal.ZERO, BigDecimal.ZERO,
            0L, null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"lastMoveAt\":null");
    }
}
