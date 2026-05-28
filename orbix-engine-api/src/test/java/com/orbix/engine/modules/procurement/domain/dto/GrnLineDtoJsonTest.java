package com.orbix.engine.modules.procurement.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.orbix.engine.modules.common.service.IdLongAsStringSerializerModifier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pin the JSON wire shape of {@link GrnLineDto}.
 *
 * <ul>
 *   <li>{@code id}, {@code lpoOrderLineId}, {@code itemId}, {@code uomId},
 *       {@code vatGroupId} — Long FK fields stringify to JSON strings.
 *   <li>{@code itemUid}, {@code uomUid}, {@code vatGroupUid} — 26-char ULID
 *       strings, not numbers.
 *   <li>{@code itemCode}, {@code itemName}, {@code uomCode}, {@code vatGroupName}
 *       — plain strings.
 *   <li>Genuine numerics ({@code receivedQty}, {@code unitCost}, {@code lineTotal})
 *       stay numeric.
 * </ul>
 */
class GrnLineDtoJsonTest {

    private static final String ITEM_UID     = "01HZ8X7M3K9PJK2D7Q5BCN8W4F";
    private static final String UOM_UID      = "01HZ8X7M3K9PJK2D7Q5BCN8W5G";
    private static final String VAT_GRP_UID  = "01HZ8X7M3K9PJK2D7Q5BCN8W6H";

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .registerModule(new SimpleModule().setSerializerModifier(new IdLongAsStringSerializerModifier()));

    @Test
    void id_and_fk_fields_serialise_as_strings_uids_and_names_stay_strings() throws Exception {
        GrnLineDto dto = new GrnLineDto(
            101L,           // id
            601L,           // lpoOrderLineId
            8801L,          // itemId
            ITEM_UID,       // itemUid
            "SKU-001",      // itemCode
            "White Sugar",  // itemName
            1L,             // uomId
            UOM_UID,        // uomUid
            "KG",           // uomCode
            new BigDecimal("10.0000"),  // receivedQty
            new BigDecimal("90.0000"),  // unitCost
            2L,             // vatGroupId
            VAT_GRP_UID,    // vatGroupUid
            "Standard 18%", // vatGroupName
            new BigDecimal("900.0000"), // lineTotal
            null,           // batchNo
            null            // expiryDate
        );

        String json = mapper.writeValueAsString(dto);

        // Long FK fields must be JSON strings.
        assertThat(json).contains("\"id\":\"101\"");
        assertThat(json).contains("\"lpoOrderLineId\":\"601\"");
        assertThat(json).contains("\"itemId\":\"8801\"");
        assertThat(json).contains("\"uomId\":\"1\"");
        assertThat(json).contains("\"vatGroupId\":\"2\"");

        // ULID strings must NOT be numeric — they must appear as plain strings.
        assertThat(json).contains("\"itemUid\":\"" + ITEM_UID + "\"");
        assertThat(json).contains("\"uomUid\":\"" + UOM_UID + "\"");
        assertThat(json).contains("\"vatGroupUid\":\"" + VAT_GRP_UID + "\"");

        // Display strings are plain strings.
        assertThat(json).contains("\"itemCode\":\"SKU-001\"");
        assertThat(json).contains("\"itemName\":\"White Sugar\"");
        assertThat(json).contains("\"uomCode\":\"KG\"");
        assertThat(json).contains("\"vatGroupName\":\"Standard 18%\"");

        // Genuine numerics must remain numeric.
        assertThat(json).contains("\"receivedQty\":10.0000");
        assertThat(json).contains("\"unitCost\":90.0000");
        assertThat(json).contains("\"lineTotal\":900.0000");
    }
}
