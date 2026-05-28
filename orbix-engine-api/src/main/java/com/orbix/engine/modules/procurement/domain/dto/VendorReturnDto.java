package com.orbix.engine.modules.procurement.domain.dto;

import com.orbix.engine.modules.procurement.domain.entity.VendorReturn;
import com.orbix.engine.modules.procurement.domain.entity.VendorReturnLine;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnReason;
import com.orbix.engine.modules.procurement.domain.enums.VendorReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record VendorReturnDto(
    Long id,
    String uid,
    String number,
    Long companyId,
    Long branchId,
    Long supplierId,
    Long originalGrnId,
    Long originalSupplierInvoiceId,
    LocalDate returnDate,
    VendorReturnReason reason,
    BigDecimal totalAmount,
    VendorReturnStatus status,
    boolean restock,
    Instant postedAt,
    Long postedBy,
    String notes,
    List<VendorReturnLineDto> lines
) {
    public static VendorReturnDto from(VendorReturn r, List<VendorReturnLine> lines) {
        return new VendorReturnDto(
            r.getId(), r.getUid(), r.getNumber(), r.getCompanyId(), r.getBranchId(),
            r.getSupplierId(), r.getOriginalGrnId(), r.getOriginalSupplierInvoiceId(),
            r.getReturnDate(), r.getReason(), r.getTotalAmount(), r.getStatus(),
            r.isRestock(), r.getPostedAt(), r.getPostedBy(), r.getNotes(),
            lines.stream().map(VendorReturnLineDto::from).toList()
        );
    }
}
