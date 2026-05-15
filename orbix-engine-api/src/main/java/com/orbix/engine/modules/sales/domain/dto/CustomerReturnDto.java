package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.CustomerReturn;
import com.orbix.engine.modules.sales.domain.entity.CustomerReturnLine;
import com.orbix.engine.modules.sales.domain.enums.CustomerReturnStatus;
import com.orbix.engine.modules.sales.domain.enums.ReturnReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CustomerReturnDto(
    Long id,
    String number,
    Long companyId,
    Long branchId,
    Long customerId,
    Long originalInvoiceId,
    LocalDate returnDate,
    ReturnReason reason,
    BigDecimal totalAmount,
    CustomerReturnStatus status,
    boolean restock,
    Instant postedAt,
    Long postedBy,
    String notes,
    List<CustomerReturnLineDto> lines
) {
    public static CustomerReturnDto from(CustomerReturn r, List<CustomerReturnLine> lines) {
        return new CustomerReturnDto(r.getId(), r.getNumber(), r.getCompanyId(), r.getBranchId(),
            r.getCustomerId(), r.getOriginalInvoiceId(), r.getReturnDate(), r.getReason(),
            r.getTotalAmount(), r.getStatus(), r.isRestock(),
            r.getPostedAt(), r.getPostedBy(), r.getNotes(),
            lines.stream().map(CustomerReturnLineDto::from).toList());
    }
}
