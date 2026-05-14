package com.orbix.engine.modules.stock.service;

import com.orbix.engine.modules.common.service.RequestContext;
import com.orbix.engine.modules.stock.domain.dto.CreateStockTransferRequestDto;
import com.orbix.engine.modules.stock.domain.dto.PostStockMoveRequestDto;
import com.orbix.engine.modules.stock.domain.dto.ReceiveTransferRequestDto;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;
import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalanceId;
import com.orbix.engine.modules.stock.domain.entity.StockTransfer;
import com.orbix.engine.modules.stock.domain.entity.StockTransferLine;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;
import com.orbix.engine.modules.stock.domain.enums.StockTransferStatus;
import com.orbix.engine.modules.stock.repository.ItemBranchBalanceRepository;
import com.orbix.engine.modules.stock.repository.StockTransferLineRepository;
import com.orbix.engine.modules.stock.repository.StockTransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockTransferServiceImplTest {

    private static final Long COMPANY_ID = 7L;
    private static final Long FROM_BRANCH = 12L;
    private static final Long TO_BRANCH = 13L;
    private static final Long ACTOR_ID = 4L;

    @Mock private StockTransferRepository transfers;
    @Mock private StockTransferLineRepository transferLines;
    @Mock private ItemBranchBalanceRepository balances;
    @Mock private StockMoveService stockMoveService;
    @Mock private RequestContext context;

    @InjectMocks private StockTransferServiceImpl service;

    @BeforeEach
    void bind() {
        lenient().when(context.companyId()).thenReturn(COMPANY_ID);
        lenient().when(context.userId()).thenReturn(ACTOR_ID);
        lenient().when(transfers.save(any(StockTransfer.class))).thenAnswer(inv -> {
            StockTransfer t = inv.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
        lenient().when(transferLines.save(any(StockTransferLine.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private StockTransfer transfer(StockTransferStatus status) {
        StockTransfer t = new StockTransfer("TR-1", COMPANY_ID, FROM_BRANCH, TO_BRANCH);
        t.setId(1L);
        t.setStatus(status);
        return t;
    }

    private static StockTransferLine line(Long id, BigDecimal issuedQty) {
        StockTransferLine l = new StockTransferLine(1L, 8801L, issuedQty);
        l.setId(id);
        return l;
    }

    @Test
    void createTransfer_rejectsSameSourceAndDestination() {
        assertThatThrownBy(() -> service.createTransfer(new CreateStockTransferRequestDto(
            "TR-1", FROM_BRANCH, FROM_BRANCH,
            List.of(new CreateStockTransferRequestDto.TransferLine(8801L, new BigDecimal("5"))))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must differ");
    }

    @Test
    void createTransfer_rejectsDuplicateNumber() {
        when(transfers.existsByCompanyIdAndNumber(COMPANY_ID, "TR-1")).thenReturn(true);

        assertThatThrownBy(() -> service.createTransfer(new CreateStockTransferRequestDto(
            "TR-1", FROM_BRANCH, TO_BRANCH,
            List.of(new CreateStockTransferRequestDto.TransferLine(8801L, new BigDecimal("5"))))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void issueTransfer_postsTransferOutAndFreezesCost() {
        StockTransfer t = transfer(StockTransferStatus.DRAFT);
        StockTransferLine l = line(10L, new BigDecimal("5"));
        when(transfers.findById(1L)).thenReturn(Optional.of(t));
        when(transferLines.findByStockTransferId(1L)).thenReturn(List.of(l));
        ItemBranchBalance bal = new ItemBranchBalance(8801L, FROM_BRANCH);
        bal.setAvgCost(new BigDecimal("480"));
        when(balances.findById(new ItemBranchBalanceId(8801L, FROM_BRANCH))).thenReturn(Optional.of(bal));

        service.issueTransfer(1L);

        assertThat(t.getStatus()).isEqualTo(StockTransferStatus.ISSUED);
        assertThat(l.getCostAmount()).isEqualByComparingTo("480");
        ArgumentCaptor<PostStockMoveRequestDto> move = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(move.capture());
        assertThat(move.getValue().qty()).isEqualByComparingTo("-5");
        assertThat(move.getValue().moveType()).isEqualTo(StockMoveType.TRANSFER_OUT);
    }

    @Test
    void receiveTransfer_postsTransferInForReceivedQty() {
        StockTransfer t = transfer(StockTransferStatus.ISSUED);
        StockTransferLine l = line(10L, new BigDecimal("5"));
        l.setCostAmount(new BigDecimal("480"));
        when(transfers.findById(1L)).thenReturn(Optional.of(t));
        when(transferLines.findByStockTransferId(1L)).thenReturn(List.of(l));

        service.receiveTransfer(1L, new ReceiveTransferRequestDto(
            List.of(new ReceiveTransferRequestDto.ReceiveLine(10L, new BigDecimal("5")))));

        assertThat(t.getStatus()).isEqualTo(StockTransferStatus.RECEIVED);
        assertThat(l.getReceivedQty()).isEqualByComparingTo("5");
        ArgumentCaptor<PostStockMoveRequestDto> move = ArgumentCaptor.forClass(PostStockMoveRequestDto.class);
        verify(stockMoveService).post(move.capture());
        assertThat(move.getValue().branchId()).isEqualTo(TO_BRANCH);
        assertThat(move.getValue().qty()).isEqualByComparingTo("5");
        assertThat(move.getValue().moveType()).isEqualTo(StockMoveType.TRANSFER_IN);
    }

    @Test
    void receiveTransfer_rejectsTransferNotIssued() {
        when(transfers.findById(1L)).thenReturn(Optional.of(transfer(StockTransferStatus.DRAFT)));

        assertThatThrownBy(() -> service.receiveTransfer(1L, new ReceiveTransferRequestDto(
            List.of(new ReceiveTransferRequestDto.ReceiveLine(10L, BigDecimal.ONE)))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ISSUED");
        verify(stockMoveService, never()).post(any());
    }
}
