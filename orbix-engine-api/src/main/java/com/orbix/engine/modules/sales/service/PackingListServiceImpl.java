package com.orbix.engine.modules.sales.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.sales.domain.dto.CreatePackingListRequestDto;
import com.orbix.engine.modules.sales.domain.dto.PackingListDto;
import com.orbix.engine.modules.sales.domain.entity.PackingList;
import com.orbix.engine.modules.sales.domain.entity.PackingListLine;
import com.orbix.engine.modules.sales.domain.entity.SalesInvoice;
import com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus;
import com.orbix.engine.modules.sales.repository.PackingListLineRepository;
import com.orbix.engine.modules.sales.repository.PackingListRepository;
import com.orbix.engine.modules.sales.repository.SalesInvoiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PackingListServiceImpl implements PackingListService {

    private static final String AGG = "PackingList";
    private static final String F_ID = "packingListId";
    private static final String F_NUMBER = "number";

    private final PackingListRepository packingLists;
    private final PackingListLineRepository lines;
    private final SalesInvoiceRepository invoices;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = AGG)
    public PackingListDto createDraft(CreatePackingListRequestDto request) {
        Long companyId = context.companyId();
        Long actorId = context.userId();
        branchScope.requireAccess(request.branchId());
        String number = request.number().trim().toUpperCase();
        if (packingLists.existsByBranchIdAndNumber(request.branchId(), number)) {
            throw new IllegalArgumentException(
                "Packing-list number already exists for this branch: " + number);
        }
        SalesInvoice invoice = invoices.findById(request.salesInvoiceId())
            .orElseThrow(() -> new NoSuchElementException(
                "Sales invoice not found: " + request.salesInvoiceId()));
        if (!Objects.equals(invoice.getCompanyId(), companyId)) {
            throw new NoSuchElementException(
                "Sales invoice not found: " + request.salesInvoiceId());
        }
        if (invoice.getStatus() != SalesInvoiceStatus.POSTED
                && invoice.getStatus() != SalesInvoiceStatus.PARTIALLY_PAID
                && invoice.getStatus() != SalesInvoiceStatus.PAID) {
            throw new IllegalArgumentException(
                "Packing lists can only be created against POSTED / PARTIALLY_PAID / PAID invoices (was "
                    + invoice.getStatus() + ")");
        }

        PackingList pl = packingLists.save(new PackingList(
            number, companyId, request.branchId(), request.salesInvoiceId(),
            request.dispatchDate(), request.driverName(), request.vehicleNo(),
            request.notes(), actorId
        ));
        List<PackingListLine> savedLines = new ArrayList<>(request.lines().size());
        for (CreatePackingListRequestDto.Line input : request.lines()) {
            savedLines.add(lines.save(new PackingListLine(
                pl.getId(), input.salesInvoiceLineId(), input.qty()
            )));
        }
        events.publish("PackingListCreated.v1", AGG, String.valueOf(pl.getId()),
            Map.of(F_ID, pl.getId(), F_NUMBER, pl.getNumber(),
                "salesInvoiceId", pl.getSalesInvoiceId()));
        return PackingListDto.from(pl, savedLines);
    }

    @Override
    @Transactional
    @Auditable(action = "DISPATCH", entityType = AGG)
    public PackingListDto dispatch(String uid) {
        PackingList pl = requirePackingListByUid(uid);
        pl.dispatch(context.userId());
        events.publish("PackingListDispatched.v1", AGG, String.valueOf(pl.getId()),
            Map.of(F_ID, pl.getId(), F_NUMBER, pl.getNumber(),
                "vehicleNo", Objects.toString(pl.getVehicleNo(), ""),
                "driverName", Objects.toString(pl.getDriverName(), "")));
        return PackingListDto.from(pl, lines.findByPackingListIdOrderByIdAsc(pl.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "DELIVER", entityType = AGG)
    public PackingListDto markDelivered(String uid) {
        PackingList pl = requirePackingListByUid(uid);
        pl.markDelivered(context.userId());
        events.publish("PackingListDelivered.v1", AGG, String.valueOf(pl.getId()),
            Map.of(F_ID, pl.getId(), F_NUMBER, pl.getNumber(),
                "deliveredBy", pl.getDeliveredBy()));
        return PackingListDto.from(pl, lines.findByPackingListIdOrderByIdAsc(pl.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "CANCEL", entityType = AGG)
    public PackingListDto cancel(String uid) {
        PackingList pl = requirePackingListByUid(uid);
        pl.cancel(context.userId());
        events.publish("PackingListCancelled.v1", AGG, String.valueOf(pl.getId()),
            Map.of(F_ID, pl.getId(), F_NUMBER, pl.getNumber()));
        return PackingListDto.from(pl, lines.findByPackingListIdOrderByIdAsc(pl.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackingListDto> list(Long branchId) {
        Long companyId = context.companyId();
        Long scope = branchScope.requireReadable(branchId);
        List<PackingList> rows = scope == null
            ? packingLists.findByCompanyIdOrderByIdDesc(companyId)
            : packingLists.findByCompanyIdAndBranchIdOrderByIdDesc(companyId, scope);
        return rows.stream()
            .map(p -> PackingListDto.from(p, lines.findByPackingListIdOrderByIdAsc(p.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PackingListDto get(String uid) {
        PackingList pl = requirePackingListByUid(uid);
        return PackingListDto.from(pl, lines.findByPackingListIdOrderByIdAsc(pl.getId()));
    }

    private PackingList requirePackingListByUid(String uid) {
        PackingList pl = packingLists.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Packing list not found: " + uid));
        if (!Objects.equals(pl.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Packing list not found: " + uid);
        }
        branchScope.requireAccess(pl.getBranchId());
        return pl;
    }
}
