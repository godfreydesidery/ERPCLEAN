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
import com.orbix.engine.modules.procurement.domain.dto.CreateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.dto.LpoOrderDto;
import com.orbix.engine.modules.procurement.domain.dto.UpdateLpoOrderRequestDto;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrder;
import com.orbix.engine.modules.procurement.domain.entity.LpoOrderLine;
import com.orbix.engine.modules.procurement.domain.enums.LpoOrderStatus;
import com.orbix.engine.modules.procurement.repository.LpoOrderLineRepository;
import com.orbix.engine.modules.procurement.repository.LpoOrderRepository;
import lombok.RequiredArgsConstructor;
import com.orbix.engine.modules.common.domain.enums.SettingKey;
import com.orbix.engine.modules.common.service.SettingsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class LpoOrderServiceImpl implements LpoOrderService {

    private static final int MONEY_SCALE = 4;
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private static final String AGG = "LpoOrder";
    private static final String F_ID = "lpoOrderId";
    private static final String F_NUMBER = "number";
    private static final String F_TOTAL = "totalAmount";

    private final LpoOrderRepository orders;
    private final LpoOrderLineRepository lines;
    private final ItemRepository items;
    private final VatGroupRepository vatGroups;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;
    private final SettingsService settings;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public LpoOrderDto createDraft(CreateLpoOrderRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        if (orders.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException("LPO number already exists for this branch: " + number);
        }
        LpoOrder order = orders.save(new LpoOrder(
            number, companyId, request.branchId(), request.supplierId(),
            request.orderDate(), request.expectedDeliveryDate(),
            request.currencyCode(), request.notes(), actorId
        ));
        List<LpoOrderLine> savedLines = saveLinesAndRollUp(order, request.lines(), companyId);
        events.publish("LpoOrderCreated.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "supplierId", order.getSupplierId(), F_TOTAL, order.getTotalAmount()));
        return LpoOrderDto.from(order, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "UPDATE", entityType = AGG)
    public LpoOrderDto updateDraft(Long lpoId, UpdateLpoOrderRequestDto request) {
        LpoOrder order = requireOrder(lpoId);
        order.editHeader(request.supplierId(), request.orderDate(), request.expectedDeliveryDate(),
            request.currencyCode(), request.notes(), context.userId());
        lines.deleteByLpoOrderId(order.getId());
        List<LpoOrderLine> savedLines = saveLinesAndRollUp(order, request.lines(), context.companyId());
        return LpoOrderDto.from(order, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "SUBMIT", entityType = AGG)
    public LpoOrderDto submit(Long lpoId) {
        LpoOrder order = requireOrder(lpoId);
        BigDecimal autoApprovalThreshold = settings.getDecimal(SettingKey.PROCUREMENT_LPO_AUTO_APPROVAL);
        boolean autoApprove = autoApprovalThreshold.signum() > 0
            && order.getTotalAmount().compareTo(autoApprovalThreshold) <= 0;
        if (autoApprove) {
            order.approve(context.userId());
            publishApproved(order, true);
        } else {
            order.submit(context.userId());
            events.publish("LpoOrderSubmitted.v1", AGG, String.valueOf(order.getId()),
                Map.of(F_ID, order.getId(), F_TOTAL, order.getTotalAmount()));
        }
        return LpoOrderDto.from(order, lines.findByLpoOrderIdOrderByLineNoAsc(order.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "APPROVE", entityType = AGG)
    public LpoOrderDto approve(Long lpoId) {
        LpoOrder order = requireOrder(lpoId);
        if (order.getStatus() != LpoOrderStatus.PENDING_APPROVAL) {
            throw new IllegalStateException(
                "Only PENDING_APPROVAL can be approved here (was " + order.getStatus() + ")");
        }
        order.approve(context.userId());
        publishApproved(order, false);
        return LpoOrderDto.from(order, lines.findByLpoOrderIdOrderByLineNoAsc(order.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public LpoOrderDto cancel(Long lpoId) {
        LpoOrder order = requireOrder(lpoId);
        order.cancel(context.userId());
        events.publish("LpoOrderCancelled.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber()));
        return LpoOrderDto.from(order, lines.findByLpoOrderIdOrderByLineNoAsc(order.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public PageDto<LpoOrderDto> list(Long branchId, Pageable pageable) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        Page<LpoOrder> page = scope == null
            ? orders.findByCompanyIdOrderByIdDesc(companyId, pageable)
            : orders.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope, pageable);
        return PageDto.of(page, o -> LpoOrderDto.from(o, lines.findByLpoOrderIdOrderByLineNoAsc(o.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public LpoOrderDto get(Long lpoId) {
        LpoOrder order = requireOrder(lpoId);
        return LpoOrderDto.from(order, lines.findByLpoOrderIdOrderByLineNoAsc(order.getId()));
    }

    private List<LpoOrderLine> saveLinesAndRollUp(LpoOrder order,
                                                  List<CreateLpoOrderRequestDto.Line> requestLines,
                                                  Long companyId) {
        List<LpoOrderLine> saved = new ArrayList<>(requestLines.size());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        int lineNo = 1;
        for (CreateLpoOrderRequestDto.Line input : requestLines) {
            Item item = requireItem(input.itemId(), companyId);
            Long uomId = input.uomId() != null ? input.uomId() : item.getUomId();
            Long vatGroupId = input.vatGroupId() != null ? input.vatGroupId() : item.getVatGroupId();
            VatGroup vatGroup = requireVatGroup(vatGroupId, companyId);
            BigDecimal discountPct = input.discountPct() != null ? input.discountPct() : BigDecimal.ZERO;
            BigDecimal gross = input.orderedQty().multiply(input.unitPrice());
            BigDecimal discountFactor = BigDecimal.ONE.subtract(discountPct.divide(HUNDRED, MONEY_SCALE, RoundingMode.HALF_UP));
            BigDecimal lineTotal = gross.multiply(discountFactor).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal lineTax = lineTotal.multiply(vatGroup.getRate()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

            LpoOrderLine line = lines.save(new LpoOrderLine(
                order.getId(), lineNo++, input.itemId(), uomId,
                input.orderedQty(), input.unitPrice(), vatGroupId,
                discountPct, lineTotal
            ));
            saved.add(line);
            subtotal = subtotal.add(lineTotal);
            tax = tax.add(lineTax);
        }
        order.rollUpTotals(subtotal, tax);
        return saved;
    }

    private void publishApproved(LpoOrder order, boolean autoApproved) {
        events.publish("LpoOrderApproved.v1", AGG, String.valueOf(order.getId()),
            Map.of(F_ID, order.getId(), F_NUMBER, order.getNumber(),
                "supplierId", order.getSupplierId(), F_TOTAL, order.getTotalAmount(),
                "autoApproved", autoApproved));
    }

    private LpoOrder requireOrder(Long id) {
        LpoOrder order = orders.findById(id)
            .orElseThrow(() -> new NoSuchElementException("LPO not found: " + id));
        if (!Objects.equals(order.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("LPO not found: " + id);
        }
        branchScope.requireAccess(order.getBranchId());
        return order;
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
