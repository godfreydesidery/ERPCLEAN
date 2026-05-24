package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.stock.domain.dto.CreateStockTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.ReceiveTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.StockTransferDto;

import java.util.List;

/**
 * Inter-branch transfers (F2.3). Lifecycle DRAFT -> ISSUED -> RECEIVED -> CLOSED.
 * Issue posts TRANSFER_OUT moves from the source branch (cost frozen here);
 * receipt posts TRANSFER_IN moves into the destination branch at that cost.
 */
public interface StockTransferService {

    List<StockTransferDto> listTransfers();

    StockTransferDto getTransfer(String uid);

    StockTransferDto createTransfer(CreateStockTransferRequestDto request);

    /** DRAFT -> ISSUED: posts TRANSFER_OUT moves, freezing each line's cost. */
    StockTransferDto issueTransfer(String uid);

    /** ISSUED -> RECEIVED: posts TRANSFER_IN moves for the received quantities. */
    StockTransferDto receiveTransfer(String uid, ReceiveTransferRequestDto request);

    StockTransferDto closeTransfer(String uid);
}
