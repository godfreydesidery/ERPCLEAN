package com.orbix.engine.modules.procurement.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link VendorCreditNoteAllocationDto}: {@code id}
 * and {@code *Id} fields serialise as JSON strings. Genuine numerics (amount)
 * stay numeric.
 */
class VendorCreditNoteAllocationDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings() throws Exception {
        VendorCreditNoteAllocationDto dto = new VendorCreditNoteAllocationDto(
            10L,
            500L,
            "INV-0001",
            new BigDecimal("350.0000"),
            Instant.parse("2026-05-28T10:00:00Z"),
            "procurement.officer"
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"10\"");
        assertThat(json).contains("\"supplierInvoiceId\":\"500\"");
        assertThat(json).contains("\"supplierInvoiceNumber\":\"INV-0001\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"amount\":350.0000");
    }
}
