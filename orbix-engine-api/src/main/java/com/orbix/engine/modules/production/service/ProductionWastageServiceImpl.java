package com.orbix.engine.modules.production.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.production.domain.dto.ProductionWastageDto;
import com.orbix.engine.modules.production.domain.dto.RecordWastageRequestDto;
import com.orbix.engine.modules.production.domain.entity.ProductionBatch;
import com.orbix.engine.modules.production.domain.entity.ProductionWastage;
import com.orbix.engine.modules.production.domain.enums.ProductionBatchStatus;
import com.orbix.engine.modules.production.repository.ProductionBatchRepository;
import com.orbix.engine.modules.production.repository.ProductionWastageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductionWastageServiceImpl implements ProductionWastageService {

    private static final String AGG = "ProductionWastage";

    private final ProductionWastageRepository wastage;
    private final ProductionBatchRepository batches;
    private final ItemRepository items;
    private final EventPublisher events;
    private final RequestContext context;

    @Override
    @Transactional
    @Auditable(action = "RECORD", entityType = AGG)
    public ProductionWastageDto record(RecordWastageRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        ProductionBatch batch = requireBatch(request.productionBatchId());
        if (batch.getStatus() == ProductionBatchStatus.CANCELLED) {
            throw new IllegalStateException(
                "Cannot record wastage against a CANCELLED batch: " + batch.getNumber());
        }
        Item item = requireItem(request.itemId(), companyId);
        Long uomId = request.uomId() != null ? request.uomId() : item.getUomId();

        ProductionWastage row = wastage.save(new ProductionWastage(
            batch.getId(), item.getId(), request.qty(), uomId,
            request.category(), request.reason(), actorId));
        events.publish("ProductionWastageRecorded.v1", AGG, String.valueOf(row.getId()),
            Map.of("productionBatchId", batch.getId(),
                "wastageId", row.getId(),
                "itemId", item.getId(),
                "qty", row.getQty(),
                "category", row.getCategory().name()));
        return ProductionWastageDto.from(row);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductionWastageDto> listForBatch(Long productionBatchId) {
        requireBatch(productionBatchId);
        return wastage.findByProductionBatchIdOrderByRecordedAtAsc(productionBatchId).stream()
            .map(ProductionWastageDto::from)
            .toList();
    }

    private ProductionBatch requireBatch(Long id) {
        ProductionBatch batch = batches.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Production batch not found: " + id));
        if (!Objects.equals(batch.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Production batch not found: " + id);
        }
        return batch;
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }
}
