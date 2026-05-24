package com.orbix.engine.modules.procurement.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link SupplierInvoiceDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as JSON
 * strings (JSON:API discipline, driven by {@link IdLongAsStringSerializerModifier}).
 * Genuine numerics stay numeric.
 */
class SupplierInvoiceDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        SupplierInvoiceDto dto = new SupplierInvoiceDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "SI-0001",
            "INV-9000",
            2L,
            5L,
            808L,
            LocalDate.of(2026, 5, 10),
            LocalDate.of(2026, 6, 9),
            "TZS",
            new BigDecimal("1000.0000"),
            new BigDecimal("180.0000"),
            new BigDecimal("1180.0000"),
            new BigDecimal("0.0000"),
            SupplierInvoiceStatus.DRAFT,
            null,
            null,
            null,
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"supplierId\":\"808\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":1180.0000");
    }
}
