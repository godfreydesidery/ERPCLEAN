package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockCount;
import com.orbix.engine.modules.stock.domain.entity.StockCountLine;
import com.orbix.engine.modules.stock.domain.enums.StockCountStatus;
import com.orbix.engine.modules.stock.domain.enums.StockCountType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StockCountDto(
    Long id,
    String number,
    Long branchId,
    Long companyId,
    LocalDate countDate,
    StockCountType type,
    StockCountStatus status,
    Long startedBy,
    Long closedBy,
    Instant postedAt,
    List<StockCountLineDto> lines
) {
    public static StockCountDto from(StockCount count, List<StockCountLine> lines) {
        return new StockCountDto(
            count.getId(), count.getNumber(), count.getBranchId(), count.getCompanyId(),
            count.getCountDate(), count.getType(), count.getStatus(), count.getStartedBy(),
            count.getClosedBy(), count.getPostedAt(),
            lines.stream().map(StockCountLineDto::from).toList());
    }
}
