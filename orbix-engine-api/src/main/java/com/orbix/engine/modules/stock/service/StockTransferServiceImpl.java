package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.EventPublisher;
import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.iam.service.BranchScope;
import com.orbix.engine.modules.stock.domain.dto.CreateStockTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.ReceiveTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockTransferDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockTransfer;
import com.orbix.engine.modules.stock.domain.entity.StockTransferLine;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.domain.enums.StockTransferStatus;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockTransferLineRepository;
import com.orbix.engine.modules.stock.repository.StockTransferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {

    private final StockTransferRepository transfers;
    private final StockTransferLineRepository transferLines;
    private final ItemBranchBalanceRepository balances;
    private final StockMoveService stockMoveService;
    private final EventPublisher events;
    private final RequestContext context;
    private final BranchScope branchScope;

    @Override
    @Transactional(readOnly = true)
    public List<StockTransferDto> listTransfers() {
        Long companyId = context.companyId();
        // Bi-branch resource: a branch-scoped caller sees transfers touching
        // their branch on either side; a company-wide caller sees everything.
        List<StockTransfer> rows = branchScope.isCompanyWide()
            ? transfers.findByCompanyIdOrderByIdDesc(companyId)
            : transfers.findInvolvingBranch(companyId, context.branchId());
        return rows.stream()
            .map(t -> StockTransferDto.from(t, transferLines.findByStockTransferId(t.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferDto getTransfer(String uid) {
        StockTransfer transfer = requireTransferByUid(uid);
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transfer.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "StockTransfer")
    public StockTransferDto createTransfer(CreateStockTransferRequestDto request) {
        Long companyId = context.companyId();
        // Creator must hold a grant in the source branch; receivers at the
        // destination authorise themselves via receiveTransfer.
        branchScope.requireAccess(request.fromBranchId());
        String number = request.number().trim().toUpperCase();
        if (request.fromBranchId().equals(request.toBranchId())) {
            throw new IllegalArgumentException("Transfer source and destination must differ");
        }
        if (transfers.existsByCompanyIdAndNumber(companyId, number)) {
            throw new IllegalArgumentException("Stock transfer number already exists: " + number);
        }
        StockTransfer transfer = transfers.save(new StockTransfer(
            number, companyId, request.fromBranchId(), request.toBranchId()));
        for (CreateStockTransferRequestDto.TransferLine line : request.lines()) {
            transferLines.save(new StockTransferLine(transfer.getId(), line.itemId(), line.issuedQty()));
        }
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transfer.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "ISSUE", entityType = "StockTransfer")
    public StockTransferDto issueTransfer(String uid) {
        StockTransfer transfer = requireTransferByUid(uid);
        branchScope.requireAccess(transfer.getFromBranchId());
        transfer.issue();
        List<StockTransferLine> lines = transferLines.findByStockTransferId(transfer.getId());
        BigDecimal totalCost = BigDecimal.ZERO;
        List<Map<String, Object>> linePayload = new ArrayList<>();
        for (StockTransferLine line : lines) {
            BigDecimal cost = balances
                .findById(new ItemBranchBalanceId(line.getItemId(), transfer.getFromBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            line.setCostAmount(cost);
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), transfer.getFromBranchId(), line.getIssuedQty().negate(), cost,
                StockMoveType.TRANSFER_OUT, "StockTransfer", transfer.getId(), null, false));
            totalCost = totalCost.add(line.getIssuedQty().multiply(cost));
            Map<String, Object> entry = new HashMap<>();
            entry.put("itemId", line.getItemId());
            entry.put("issuedQty", line.getIssuedQty());
            entry.put("cost", cost);
            linePayload.add(entry);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("stockTransferId", transfer.getId());
        payload.put("uid", transfer.getUid());
        payload.put("number", transfer.getNumber());
        payload.put("fromBranchId", transfer.getFromBranchId());
        payload.put("toBranchId", transfer.getToBranchId());
        payload.put("totalCost", totalCost);
        payload.put("lines", linePayload);
        payload.put("actorId", context.userId());
        events.publish("StockTransferIssued.v1", "StockTransfer",
            String.valueOf(transfer.getId()), payload);
        return StockTransferDto.from(transfer, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "RECEIVE", entityType = "StockTransfer")
    public StockTransferDto receiveTransfer(String uid, ReceiveTransferRequestDto request) {
        StockTransfer transfer = requireTransferByUid(uid);
        branchScope.requireAccess(transfer.getToBranchId());
        if (transfer.getStatus() != StockTransferStatus.ISSUED) {
            throw new IllegalArgumentException("Only an ISSUED transfer can be received");
        }
        Map<Long, StockTransferLine> linesById = transferLines.findByStockTransferId(transfer.getId()).stream()
            .collect(Collectors.toMap(StockTransferLine::getId, Function.identity()));
        List<Map<String, Object>> linePayload = new ArrayList<>();
        for (ReceiveTransferRequestDto.ReceiveLine entry : request.lines()) {
            StockTransferLine line = linesById.get(entry.lineId());
            if (line == null) {
                throw new NoSuchElementException("Line not on this transfer: " + entry.lineId());
            }
            line.recordReceived(entry.receivedQty());
            if (entry.receivedQty().signum() > 0) {
                stockMoveService.post(new PostStockMoveRequestDto(
                    line.getItemId(), transfer.getToBranchId(), entry.receivedQty(),
                    line.getCostAmount(), StockMoveType.TRANSFER_IN, "StockTransfer", transfer.getId(),
                    null, false));
            }
            Map<String, Object> linkRow = new HashMap<>();
            linkRow.put("itemId", line.getItemId());
            linkRow.put("issuedQty", line.getIssuedQty());
            linkRow.put("receivedQty", entry.receivedQty());
            linePayload.add(linkRow);
        }
        transfer.receive();
        Map<String, Object> payload = new HashMap<>();
        payload.put("stockTransferId", transfer.getId());
        payload.put("uid", transfer.getUid());
        payload.put("fromBranchId", transfer.getFromBranchId());
        payload.put("toBranchId", transfer.getToBranchId());
        payload.put("lines", linePayload);
        payload.put("actorId", context.userId());
        events.publish("StockTransferReceived.v1", "StockTransfer",
            String.valueOf(transfer.getId()), payload);
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transfer.getId()));
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = "StockTransfer")
    public StockTransferDto closeTransfer(String uid) {
        StockTransfer transfer = requireTransferByUid(uid);
        transfer.close();
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transfer.getId()));
    }

    private StockTransfer requireTransferByUid(String uid) {
        StockTransfer transfer = transfers.findByUid(uid)
            .orElseThrow(() -> new NoSuchElementException("Stock transfer not found: " + uid));
        if (!Objects.equals(transfer.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Stock transfer not found: " + uid);
        }
        branchScope.requireAccessToEither(transfer.getFromBranchId(), transfer.getToBranchId());
        return transfer;
    }
}
