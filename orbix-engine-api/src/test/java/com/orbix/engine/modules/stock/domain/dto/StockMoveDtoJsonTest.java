package com.orbix.engine.modules.stock.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.stock.domain.enums.StockMoveDirection;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link StockMoveDto}: all Long id / FK fields
 * stringify; genuine numerics stay numeric; {@code at} renders as ISO-8601;
 * new enrichment fields {@code docNumber} and {@code runningBalance} are
 * present when populated and null when not.
 */
class StockMoveDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()))
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void fk_ids_stringify_numerics_stay_numerics_instants_iso() throws Exception {
        StockMoveDto dto = new StockMoveDto(
            55L,                                        // id
            Instant.parse("2026-05-01T08:00:00Z"),      // at
            8801L,                                      // itemId
            12L,                                        // branchId
            7L,                                         // companyId
            new BigDecimal("-4.0000"),                  // qty
            new BigDecimal("150.0000"),                 // costAmount
            StockMoveDirection.OUT,
            StockMoveType.SALE,
            "SalesInvoice",                             // refType
            500L,                                       // refId
            4L,                                         // actorId
            null,                                       // notes
            null,                                       // batchId
            null,                                       // sectionId
            null,                                       // consumptionCategory
            null,                                       // authorisedByUserId
            "INV-2026-0001",                            // docNumber
            new BigDecimal("96.0000")                   // runningBalance
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json)
            .contains("\"id\":\"55\"")
            .contains("\"itemId\":\"8801\"")
            .contains("\"branchId\":\"12\"")
            .contains("\"companyId\":\"7\"")
            .contains("\"refId\":\"500\"")
            .contains("\"actorId\":\"4\"")
            // genuine numerics untouched
            .contains("\"qty\":-4.0000")
            .contains("\"costAmount\":150.0000")
            .contains("\"runningBalance\":96.0000")
            // Instant as ISO-8601
            .contains("\"at\":\"2026-05-01T08:00:00Z\"")
            // enrichment string field
            .contains("\"docNumber\":\"INV-2026-0001\"");
    }

    @Test
    void thin_factory_leaves_enrichment_fields_null() throws Exception {
        StockMoveDto dto = new StockMoveDto(
            1L, Instant.now(), 1L, 1L, 1L,
            BigDecimal.ONE, BigDecimal.ONE,
            StockMoveDirection.IN, StockMoveType.GRN,
            "Grn", 1L, 1L, null, null, null, null, null,
            null, null   // docNumber, runningBalance
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json)
            .contains("\"docNumber\":null")
            .contains("\"runningBalance\":null");
    }
}
