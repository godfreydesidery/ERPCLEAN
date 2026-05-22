package com.orbix.engine.modules.stock.domain.dto;

import java.math.BigDecimal;

/**
 * One slice of an FEFO consumption: the chosen batch, the amount drained from
 * it, and the unit cost frozen at the time of receipt. Callers turn each pick
 * into one {@code stock_move} row.
 */
public record BatchPickDto(
    Long batchId,
    String batchNo,
    BigDecimal qty,
    BigDecimal cost
) {}
