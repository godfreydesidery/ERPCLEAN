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
 * Pin the JSON wire shape of {@link ItemBranchBalanceDto}: FK id fields stringify;
 * genuine numerics stay numeric; Instants render as ISO-8601; new String enrichment
 * fields are present when populated and null when not.
 */
class ItemBranchBalanceDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()))
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void fk_ids_serialise_as_strings_numerics_stay_numeric() throws Exception {
        ItemBranchBalanceDto dto = new ItemBranchBalanceDto(
            8801L,                          // itemId
            12L,                            // branchId
            new BigDecimal("10.0000"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            new BigDecimal("150.0000"),
            new BigDecimal("155.0000"),
            new BigDecimal("5.0000"),
            new BigDecimal("100.0000"),
            "A-01",
            Instant.parse("2026-05-01T10:00:00Z"),
            "COKE-500",
            "Coca-Cola 500ml",
            "Main Store"
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"itemId\":\"8801\"");
        assertThat(json).contains("\"branchId\":\"12\"");
        // genuine numerics untouched
        assertThat(json).contains("\"qtyOnHand\":10.0000");
        assertThat(json).contains("\"avgCost\":150.0000");
        // Instant as ISO-8601
        assertThat(json).contains("\"lastMovedAt\":\"2026-05-01T10:00:00Z\"");
        // enrichment fields
        assertThat(json).contains("\"itemCode\":\"COKE-500\"");
        assertThat(json).contains("\"itemName\":\"Coca-Cola 500ml\"");
        assertThat(json).contains("\"branchName\":\"Main Store\"");
    }

    @Test
    void enrichment_fields_null_when_not_hydrated() throws Exception {
        ItemBranchBalanceDto thin = new ItemBranchBalanceDto(
            1L, 2L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, null,
            null, null, null
        );

        String json = mapper.writeValueAsString(thin);

        assertThat(json).contains("\"itemCode\":null");
        assertThat(json).contains("\"itemName\":null");
        assertThat(json).contains("\"branchName\":null");
    }
}
