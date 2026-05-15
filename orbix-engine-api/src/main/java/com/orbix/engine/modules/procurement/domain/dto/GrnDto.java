package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.entity.GrnLine;
import com.orbix.engine.modules.procurement.domain.enums.GrnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record GrnDto(
    Long id,
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
    String notes,
    List<GrnLineDto> lines
) {
    public static GrnDto from(Grn grn, List<GrnLine> lines) {
        return new GrnDto(
            grn.getId(),
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
            grn.getNotes(),
            lines.stream().map(GrnLineDto::from).toList()
        );
    }
}
