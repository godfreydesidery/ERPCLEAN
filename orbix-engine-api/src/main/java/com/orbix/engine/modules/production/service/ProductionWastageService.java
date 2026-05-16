package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.production.domain.dto.ProductionWastageDto;
import com.orbix.engine.modules.production.domain.dto.RecordWastageRequestDto;

import java.util.List;

/**
 * Category-tagged wastage recording (F7.3c). Append-only — wastage rows are
 * not editable after creation. The qty is a side-channel record (it does NOT
 * touch the stock ledger — wastage is loss that never left the production
 * cabinet); the variance report joins it back to the planned vs actual
 * yield for the parent batch.
 */
public interface ProductionWastageService {

    ProductionWastageDto record(RecordWastageRequestDto request);

    List<ProductionWastageDto> listForBatch(Long productionBatchId);
}
