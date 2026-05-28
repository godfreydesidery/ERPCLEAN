package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.sales.domain.enums.CreditNoteStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link CustomerCreditNoteDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as
 * JSON strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}). Genuine numerics stay numeric.
 */
class CustomerCreditNoteDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        CustomerCreditNoteDto dto = new CustomerCreditNoteDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "CN-0001",
            2L,
            5L,
            540L,
            8L,
            LocalDate.of(2026, 5, 20),
            "TZS",
            new BigDecimal("1180.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("1180.0000"),
            CreditNoteStatus.POSTED,
            null,
            null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"customerId\":\"540\"");
        assertThat(json).contains("\"customerReturnId\":\"8\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":1180.0000");
        assertThat(json).contains("\"availableAmount\":1180.0000");
    }
}
