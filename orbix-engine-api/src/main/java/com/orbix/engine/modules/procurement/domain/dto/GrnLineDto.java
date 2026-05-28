package com.orbix.engine.modules.procurement.domain.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response DTO for a single GRN line.
 *
 * <p>uid fields ({@code itemUid}, {@code uomUid}, {@code vatGroupUid}) and display
 * fields ({@code itemName}, {@code itemCode}, {@code uomCode}, {@code vatGroupName})
 * are hydrated in {@link com.orbix.engine.modules.procurement.service.GrnServiceImpl}
 * via catalog-repository look-ups so that the frontend picker can pre-fill the
 * vendor-return create form without a second round-trip.
 *
 * <p>The static {@code from(GrnLine)} factory has been intentionally removed: it
 * cannot populate the new uid/name fields without access to catalog repositories.
 * All callers must use
 * {@link com.orbix.engine.modules.procurement.service.GrnServiceImpl#toLineDto}.
 */
public record GrnLineDto(
    Long id,
    Long lpoOrderLineId,
    Long itemId,
    String itemUid,
    String itemCode,
    String itemName,
    Long uomId,
    String uomUid,
    String uomCode,
    BigDecimal receivedQty,
    BigDecimal unitCost,
    Long vatGroupId,
    String vatGroupUid,
    String vatGroupName,
    BigDecimal lineTotal,
    String batchNo,
    LocalDate expiryDate
) {}
