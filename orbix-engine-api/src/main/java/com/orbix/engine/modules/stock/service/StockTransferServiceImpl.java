package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.Auditable;
import com.orbix.engine.modules.common.service.RequestContext;
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
    private final RequestContext context;

    @Override
    @Transactional(readOnly = true)
    public List<StockTransferDto> listTransfers() {
        return transfers.findByCompanyIdOrderByIdDesc(context.companyId()).stream()
            .map(t -> StockTransferDto.from(t, transferLines.findByStockTransferId(t.getId())))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferDto getTransfer(Long transferId) {
        StockTransfer transfer = requireTransfer(transferId);
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transferId));
    }

    @Override
    @Transactional
    @Auditable(action = "CREATE", entityType = "StockTransfer")
    public StockTransferDto createTransfer(CreateStockTransferRequestDto request) {
        Long companyId = context.companyId();
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
    public StockTransferDto issueTransfer(Long transferId) {
        StockTransfer transfer = requireTransfer(transferId);
        transfer.issue();
        List<StockTransferLine> lines = transferLines.findByStockTransferId(transferId);
        for (StockTransferLine line : lines) {
            BigDecimal cost = balances
                .findById(new ItemBranchBalanceId(line.getItemId(), transfer.getFromBranchId()))
                .map(ItemBranchBalance::getAvgCost)
                .orElse(BigDecimal.ZERO);
            line.setCostAmount(cost);
            stockMoveService.post(new PostStockMoveRequestDto(
                line.getItemId(), transfer.getFromBranchId(), line.getIssuedQty().negate(), cost,
                StockMoveType.TRANSFER_OUT, "StockTransfer", transferId, null, false));
        }
        return StockTransferDto.from(transfer, lines);
    }

    @Override
    @Transactional
    @Auditable(action = "RECEIVE", entityType = "StockTransfer")
    public StockTransferDto receiveTransfer(Long transferId, ReceiveTransferRequestDto request) {
        StockTransfer transfer = requireTransfer(transferId);
        if (transfer.getStatus() != StockTransferStatus.ISSUED) {
            throw new IllegalArgumentException("Only an ISSUED transfer can be received");
        }
        Map<Long, StockTransferLine> linesById = transferLines.findByStockTransferId(transferId).stream()
            .collect(Collectors.toMap(StockTransferLine::getId, Function.identity()));
        for (ReceiveTransferRequestDto.ReceiveLine entry : request.lines()) {
            StockTransferLine line = linesById.get(entry.lineId());
            if (line == null) {
                throw new NoSuchElementException("Line not on this transfer: " + entry.lineId());
            }
            line.recordReceived(entry.receivedQty());
            if (entry.receivedQty().signum() > 0) {
                stockMoveService.post(new PostStockMoveRequestDto(
                    line.getItemId(), transfer.getToBranchId(), entry.receivedQty(),
                    line.getCostAmount(), StockMoveType.TRANSFER_IN, "StockTransfer", transferId,
                    null, false));
            }
        }
        transfer.receive();
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transferId));
    }

    @Override
    @Transactional
    @Auditable(action = "CLOSE", entityType = "StockTransfer")
    public StockTransferDto closeTransfer(Long transferId) {
        StockTransfer transfer = requireTransfer(transferId);
        transfer.close();
        return StockTransferDto.from(transfer, transferLines.findByStockTransferId(transferId));
    }

    private StockTransfer requireTransfer(Long transferId) {
        StockTransfer transfer = transfers.findById(transferId)
            .orElseThrow(() -> new NoSuchElementException("Stock transfer not found: " + transferId));
        if (!Objects.equals(transfer.getCompanyId(), context.companyId())) {
            throw new NoSuchElementException("Stock transfer not found: " + transferId);
        }
        return transfer;
    }
}
