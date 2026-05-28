package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GrnDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long supplierId,
    Long lpoOrderId,
    LocalDate receivedDate,
    String supplierDeliveryNote,
    BigDecimal subtotalAmount,
    BigDecimal taxAmount,
    BigDecimal totalAmount,
    GrnStatus status,
    Instant postedAt,
    Long postedBy,
    String cancellationReason,
    String notes,
    List<GrnLineDto> lines
) {
    /** Build a {@link GrnDto} from a header entity and pre-hydrated line DTOs. */
    public static GrnDto from(Grn grn, List<GrnLineDto> lines) {
        return new GrnDto(
            grn.getId(),
            grn.getUid(),
            grn.getNumber(),
            grn.getCompanyId(),
            grn.getBranchId(),
            grn.getSupplierId(),
            grn.getLpoOrderId(),
            grn.getReceivedDate(),
            grn.getSupplierDeliveryNote(),
            grn.getSubtotalAmount(),
            grn.getTaxAmount(),
            grn.getTotalAmount(),
            grn.getStatus(),
            grn.getPostedAt(),
            grn.getPostedBy(),
            grn.getCancellationReason(),
            grn.getNotes(),
            lines
        );
    }
}
