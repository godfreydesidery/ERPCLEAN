package com.orbix.engine.modules.sales.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffStatus;
import com.orbix.engine.modules.sales.domain.enums.DebtWriteOffTargetKind;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link DebtWriteOffDto}:
 * - Long {@code id} / {@code *Id} fields serialise as JSON strings.
 * - {@code amount} (genuine numeric) stays numeric on the wire.
 * - Enum fields render as strings.
 * - Nullable fields render as {@code null} when absent (non_null excluded by
 *   the global Jackson config, but the test uses a local mapper that mirrors
 *   the IdLongAsStringSerializerModifier only — nulls will appear).
 */
class DebtWriteOffDtoJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_id_fields_serialise_as_strings_amount_stays_numeric() throws Exception {
        DebtWriteOffDto dto = new DebtWriteOffDto(
            101L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W4F",
            DebtWriteOffTargetKind.CUSTOMER_INVOICE,
            500L,
            "01HZCUSTINVUID00000000001",
            "SI-2026-001",
            "Acme Corp",
            new BigDecimal("50000.0000"),
            "TZS",
            "Bad debt — customer insolvent",
            DebtWriteOffStatus.POSTED,
            7L,
            "alice",
            Instant.parse("2026-05-28T10:00:00Z"),
            9L,
            "bob",
            Instant.parse("2026-05-28T10:05:00Z"),
            Instant.parse("2026-05-28T10:05:00Z"),
            null,
            null
        );

        String json = mapper.writeValueAsString(dto);

        // Long id fields must be JSON strings.
        assertThat(json).contains("\"id\":\"101\"");
        assertThat(json).contains("\"targetInvoiceId\":\"500\"");
        assertThat(json).contains("\"requestedByUserId\":\"7\"");
        assertThat(json).contains("\"approvedByUserId\":\"9\"");

        // uid is a plain string — untouched.
        assertThat(json).contains("\"uid\":\"01HZ8X7M3K9PJK2D7Q5BCN8W4F\"");

        // amount is a genuine numeric — must NOT be quoted.
        assertThat(json).contains("\"amount\":50000.0000");

        // Enum fields render as strings.
        assertThat(json).contains("\"targetKind\":\"CUSTOMER_INVOICE\"");
        assertThat(json).contains("\"status\":\"POSTED\"");

        // Hydrated string fields.
        assertThat(json).contains("\"targetInvoiceNumber\":\"SI-2026-001\"");
        assertThat(json).contains("\"partyName\":\"Acme Corp\"");
        assertThat(json).contains("\"requestedByUsername\":\"alice\"");
        assertThat(json).contains("\"approvedByUsername\":\"bob\"");
    }

    @Test
    void pending_approval_status_and_null_approved_fields() throws Exception {
        DebtWriteOffDto dto = new DebtWriteOffDto(
            202L,
            "01HZ8X7M3K9PJK2D7Q5BCN8W5G",
            DebtWriteOffTargetKind.SUPPLIER_INVOICE,
            800L,
            "01HZSUPINVUID00000000001",
            "SINV-0042",
            "Best Supplier Ltd",
            new BigDecimal("200000.0000"),
            "TZS",
            "AP write-off",
            DebtWriteOffStatus.PENDING_APPROVAL,
            11L,
            "charlie",
            Instant.parse("2026-05-28T09:00:00Z"),
            null,
            null,
            null,
            null,
            null,
            null
        );

        String json = mapper.writeValueAsString(dto);

        assertThat(json).contains("\"id\":\"202\"");
        assertThat(json).contains("\"targetKind\":\"SUPPLIER_INVOICE\"");
        assertThat(json).contains("\"status\":\"PENDING_APPROVAL\"");
        assertThat(json).contains("\"requestedByUserId\":\"11\"");
        // amount numeric
        assertThat(json).contains("\"amount\":200000.0000");
    }
}
