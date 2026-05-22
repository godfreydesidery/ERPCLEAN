package com.orbix.engine.modules.sales.domain.dto;

import com.orbix.engine.modules.sales.domain.entity.PackingList;
import com.orbix.engine.modules.sales.domain.entity.PackingListLine;
import com.orbix.engine.modules.sales.domain.enums.PackingListStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PackingListDto(
    Long id,
    String number,
    Long companyId,
    Long branchId,
    Long salesInvoiceId,
    LocalDate dispatchDate,
    String driverName,
    String vehicleNo,
    PackingListStatus status,
    Instant deliveredAt,
    Long deliveredBy,
    String notes,
    List<PackingListLineDto> lines
) {
    public static PackingListDto from(PackingList p, List<PackingListLine> lines) {
        return new PackingListDto(p.getId(), p.getNumber(), p.getCompanyId(), p.getBranchId(),
            p.getSalesInvoiceId(), p.getDispatchDate(), p.getDriverName(), p.getVehicleNo(),
            p.getStatus(), p.getDeliveredAt(), p.getDeliveredBy(), p.getNotes(),
            lines.stream().map(PackingListLineDto::from).toList());
    }
}
