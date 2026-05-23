package com.orbix.engine.modules.procurement.service;

import com.orbix.engine.modules.catalog.domain.entity.Item;
import com.orbix.engine.modules.catalog.domain.entity.VatGroup;
import com.orbix.engine.modules.catalog.repository.ItemRepository;
import com.orbix.engine.modules.catalog.repository.VatGroupRepository;
import com.orbix.engine.modules.common.domain.dto.PageDto;
import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.procurement.domain.dto.CreateGrnRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.GrnDto;
import com.orbix.engine.modules.procurement.domain.entity.Grn;
import com.orbix.engine.modules.procurement.domain.entity.GrnLine;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import com.orbix.engine.modules.procurement.repository.GrnLineRepository;
import com.orbix.engine.modules.procurement.repository.GrnRepository;
import com.orbix.engine.modules.procurement.repository.LpoOrderLineRepository;
import com.orbix.engine.modules.procurement.repository.LpoOrderRepository;
import com.orbix.engine.modules.stock.domain.dto.CreateStockBatchRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockBatchDto;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.service.StockBatchService;
import com.orbix.engine.modules.stock.service.StockMoveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GrnServiceImpl implements GrnService {

    private static final int MONEY_SCALE = 4;
    private static final String AGG = "Grn";
    private static final String F_ID = "grnId";
    private static final String F_NUMBER = "number";
    private static final String F_LPO_ID = "lpoOrderId";

    private final GrnRepository grns;
    private final GrnLineRepository grnLines;
    private final LpoOrderRepository lpos;
    private final LpoOrderLineRepository lpoLines;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final StockMoveService stockMoveService;
    private final StockBatchService stockBatchService;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public GrnDto createDraft(CreateGrnRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        if (grns.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException("GRN number already exists for this branch: " + number);
        }

        Map<Long, LpoOrderLine> lpoLineById = resolveLpoLines(request, companyId);
        validateLines(request, lpoLineById, companyId);

        Grn grn = grns.save(new Grn(
            number, companyId, request.branchId(), request.supplierId(),
            request.lpoOrderId(), request.receivedDate(),
            request.supplierDeliveryNote(), request.notes(), actorId
        ));
        List<GrnLine> savedLines = saveLinesAndRollUp(grn, request.lines(), companyId);

        events.publish("GrnCreated.v1", AGG, String.valueOf(grn.getId()),
            Map.of(F_ID, grn.getId(), F_NUMBER, grn.getNumber(),
                "supplierId", grn.getSupplierId(),
                F_LPO_ID, grn.getLpoOrderId() != null ? grn.getLpoOrderId() : -1L));

        return GrnDto.from(grn, savedLines);
    }

    private Map<Long, LpoOrderLine> resolveLpoLines(CreateGrnRequestDto request, Long companyId) {
        if (request.lpoOrderId() == null) {
            return Map.of();
        }
        LpoOrder lpo = requireLpo(request.lpoOrderId(), companyId);
        if (lpo.getStatus() != LpoOrderStatus.APPROVED
                && lpo.getStatus() != LpoOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalArgumentException(
                "LPO " + lpo.getNumber() + " must be APPROVED or PARTIALLY_RECEIVED (was "
                    + lpo.getStatus() + ")");
        }
        if (!Objects.equals(lpo.getSupplierId(), request.supplierId())) {
            throw new IllegalArgumentException("Supplier mismatch with LPO " + lpo.getNumber());
        }
        if (!Objects.equals(lpo.getBranchId(), request.branchId())) {
            throw new IllegalArgumentException("Branch mismatch with LPO " + lpo.getNumber());
        }
        Map<Long, LpoOrderLine> map = new HashMap<>();
        for (LpoOrderLine line : lpoLines.findByLpoOrderIdOrderByLineNoAsc(lpo.getId())) {
            map.put(line.getId(), line);
        }
        return map;
    }

    /** Validate every line before any persistence so a partial failure leaves no half-written GRN. */
    private void validateLines(CreateGrnRequestDto request,
                               Map<Long, LpoOrderLine> lpoLineById, Long companyId) {
        boolean lpoBound = request.lpoOrderId() != null;
        for (CreateGrnRequestDto.Line input : request.lines()) {
            Item item = requireItem(input.itemId(), companyId);
            if (item.isBatchTracked() && (input.batchNo() == null || input.batchNo().isBlank())) {
                throw new IllegalArgumentException(
                    "Item " + item.getId() + " is batch-tracked; batchNo is required");
            }
            if (lpoBound) {
                validateAgainstLpoLine(input, lpoLineById, request.lpoOrderId());
            }
        }
    }

    private void validateAgainstLpoLine(CreateGrnRequestDto.Line input,
                                        Map<Long, LpoOrderLine> lpoLineById, Long lpoOrderId) {
        if (input.lpoOrderLineId() == null) {
            throw new IllegalArgumentException(
                "Each GRN line must reference an LPO line when bound to an LPO");
        }
        LpoOrderLine lpoLine = lpoLineById.get(input.lpoOrderLineId());
        if (lpoLine == null) {
            throw new IllegalArgumentException(
                "LPO line " + input.lpoOrderLineId() + " does not belong to LPO " + lpoOrderId);
        }
        if (!Objects.equals(lpoLine.getItemId(), input.itemId())) {
            throw new IllegalArgumentException(
                "Item mismatch with LPO line " + lpoLine.getId());
        }
        BigDecimal outstanding = lpoLine.outstandingQty();
        if (input.receivedQty().compareTo(outstanding) > 0) {
            throw new IllegalArgumentException(
                "Over-receipt on LPO line " + lpoLine.getId()
                    + ": requested " + input.receivedQty()
                    + " but outstanding is " + outstanding);
        }
    }

    private List<GrnLine> saveLinesAndRollUp(Grn grn,
                                             List<CreateGrnRequestDto.Line> requestLines,
                                             Long companyId) {
        List<GrnLine> savedLines = new ArrayList<>(requestLines.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        for (CreateGrnRequestDto.Line input : requestLines) {
            Item item = requireItem(input.itemId(), companyId);
            Long uomId = input.uomId() != null ? input.uomId() : item.getUomId();
            Long vatGroupId = input.vatGroupId() != null ? input.vatGroupId() : item.getVatGroupId();
            VatGroup vat = requireVatGroup(vatGroupId, companyId);

            BigDecimal lineTotal = input.receivedQty().multiply(input.unitCost())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineTotal.multiply(vat.getRate())
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            GrnLine line = grnLines.save(new GrnLine(
                grn.getId(), input.lpoOrderLineId(), input.itemId(), uomId,
                input.receivedQty(), input.unitCost(), vatGroupId, lineTotal,
                input.batchNo() != null ? input.batchNo().trim() : null,
                input.expiryDate()
            ));
            savedLines.add(line);
            subtotal = subtotal.add(lineTotal);
            tax = tax.add(lineTax);
        }
        grn.rollUpTotals(subtotal, tax);
        return savedLines;
    }

    @Override
    @Transactional
    @Auditable(action = "POST", entityType = AGG)
    public GrnDto post(Long grnId) {
        Grn grn = requireGrn(grnId);
        Long actorId = context.userId();
        Long companyId = context.companyId();
        List<GrnLine> lines = grnLines.findByGrnIdOrderByIdAsc(grn.getId());

        Map<Long, LpoOrderLine> lpoLineById = new HashMap<>();
        LpoOrder lpo = null;
        if (grn.getLpoOrderId() != null) {
            lpo = requireLpo(grn.getLpoOrderId(), companyId);
            for (LpoOrderLine line : lpoLines.findByLpoOrderIdOrderByLineNoAsc(lpo.getId())) {
                lpoLineById.put(line.getId(), line);
            }
        }

        for (GrnLine line : lines) {
            Item item = requireItem(line.getItemId(), companyId);
            Long batchId = null;
            if (item.isBatchTracked()) {
                StockBatchDto batch = stockBatchService.createBatch(new CreateStockBatchRequestDto(
                    line.getItemId(), grn.getBranchId(), line.getBatchNo(),
                    null, line.getExpiryDate(),
                    line.getReceivedQty(), line.getUnitCost(),
                    AGG, grn.getId()
                ));
                batchId = batch.id();
            }
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), grn.getBranchId(), line.getReceivedQty(), line.getUnitCost(),
                StockMoveType.GRN, AGG, grn.getId(), null, false, batchId
            ));
            if (line.getLpoOrderLineId() != null) {
                LpoOrderLine lpoLine = lpoLineById.get(line.getLpoOrderLineId());
                if (lpoLine != null) {
                    lpoLine.addReceived(line.getReceivedQty());
                }
            }
        }

        if (lpo != null) {
            boolean fullyReceived = lpoLineById.values().stream().allMatch(LpoOrderLine::isFullyReceived);
            lpo.markReceiveProgress(fullyReceived, actorId);
        }

        grn.post(actorId);
        events.publish("GrnPosted.v1", AGG, String.valueOf(grn.getId()),
            Map.of(F_ID, grn.getId(), F_NUMBER, grn.getNumber(),
                "supplierId", grn.getSupplierId(),
                "totalAmount", grn.getTotalAmount(),
                "lineCount", lines.size()));
        return GrnDto.from(grn, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public GrnDto cancel(Long grnId) {
        Grn grn = requireGrn(grnId);
        grn.cancel(context.userId());
        events.publish("GrnCancelled.v1", AGG, String.valueOf(grn.getId()),
            Map.of(F_ID, grn.getId(), F_NUMBER, grn.getNumber()));
        return GrnDto.from(grn, grnLines.findByGrnIdOrderByIdAsc(grn.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<GrnDto> list(Long branchId, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        Page<Grn> page = scope == null
            ? grns.findByCompanyIdOrderByIdDesc(companyId, pageable)
            : grns.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope, pageable);
        return PageDto.of(page, g -> GrnDto.from(g, grnLines.findByGrnIdOrderByIdAsc(g.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public GrnDto get(Long grnId) {
        Grn grn = requireGrn(grnId);
        return GrnDto.from(grn, grnLines.findByGrnIdOrderByIdAsc(grn.getId()));
    }

    private Grn requireGrn(Long id) {
        Grn grn = grns.findById(id)
            .orElseThrow(() -> new NoSuchElementException("GRN not found: " + id));
        if (!Objects.equals(grn.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("GRN not found: " + id);
        }
        branchScope.requireAccess(grn.getBranchId());
        return grn;
    }

    private LpoOrder requireLpo(Long id, Long companyId) {
        LpoOrder lpo = lpos.findById(id)
            .orElseThrow(() -> new NoSuchElementException("LPO not found: " + id));
        if (!Objects.equals(lpo.getCompanyId(), companyId)) {
            throw new NoSuchElementException("LPO not found: " + id);
        }
        return lpo;
    }

    private Item requireItem(Long itemId, Long companyId) {
        Item item = items.findById(itemId)
            .orElseThrow(() -> new NoSuchElementException("Item not found: " + itemId));
        if (!Objects.equals(item.getCompanyId(), companyId)) {
            throw new NoSuchElementException("Item not found: " + itemId);
        }
        return item;
    }

    private VatGroup requireVatGroup(Long vatGroupId, Long companyId) {
        VatGroup vat = vatGroups.findById(vatGroupId)
            .orElseThrow(() -> new NoSuchElementException("VAT group not found: " + vatGroupId));
        if (!Objects.equals(vat.getCompanyId(), companyId)) {
            throw new NoSuchElementException("VAT group not found: " + vatGroupId);
        }
        return vat;
    }
}
