package com.orbix.engine.modules.production.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.domain.enums.ProductionLifecycleState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link ProductionBatchDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as JSON
 * strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}). Genuine numerics stay numeric.
 */
class ProductionBatchDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        ProductionBatchDto dto = new ProductionBatchDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "BATCH-0001",
            2L,
            5L,
            7L,
            11L,
            540L,
            new BigDecimal("100.0000"),
            null,
            null,
            ProductionBatchStatus.PLANNED,
            ProductionLifecycleState.PLANNED,
            null, null, null, null,
            "notes",
            List.of(),
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"sectionId\":\"7\"");
        assertThat(json).contains("\"bomId\":\"11\"");
        assertThat(json).contains("\"outputItemId\":\"540\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"plannedQty\":100.0000");
    }
}
