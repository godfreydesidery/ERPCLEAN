package com.orbix.engine.modules.stock.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.stock.domain.enums.StockTransferStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link StockTransferDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as JSON
 * strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}).
 */
class StockTransferDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        StockTransferDto dto = new StockTransferDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "TR-0001",
            7L,
            12L,
            13L,
            null,
            null,
            StockTransferStatus.DRAFT,
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"7\"");
        assertThat(json).contains("\"fromBranchId\":\"12\"");
        assertThat(json).contains("\"toBranchId\":\"13\"");
    }
}
