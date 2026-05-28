package com.orbix.engine.modules.procurement.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnReason;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link VendorReturnDto}: {@code uid} is the
 * external identifier and {@code id} / all {@code *Id} fields serialise as
 * JSON strings (JSON:API discipline, driven by
 * {@link IdLongAsStringSerializerModifier}). Genuine numerics stay numeric.
 */
class VendorReturnDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_uid_stays_string() throws Exception {
        VendorReturnDto dto = new VendorReturnDto(
            42L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            "RET-0001",
            2L,
            5L,
            808L,
            101L,
            202L,
            LocalDate.of(2026, 5, 28),
            VendorReturnReason.DAMAGED,
            new BigDecimal("1180.0000"),
            VendorReturnStatus.DRAFT,
            true,
            null, null,
            null,
            List.of()
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"42\"");
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");
        assertThat(json).contains("\"companyId\":\"2\"");
        assertThat(json).contains("\"branchId\":\"5\"");
        assertThat(json).contains("\"supplierId\":\"808\"");
        assertThat(json).contains("\"originalGrnId\":\"101\"");
        assertThat(json).contains("\"originalSupplierInvoiceId\":\"202\"");
        // Genuine numerics untouched.
        assertThat(json).contains("\"totalAmount\":1180.0000");
    }
}
