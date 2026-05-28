package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.StockMove;
import com.orbix.engine.modules.stock.domain.enums.ConsumptionCategory;
import com.orbix.engine.modules.stock.domain.enums.StockMoveDirection;
import com.orbix.engine.modules.stock.domain.enums.StockMoveType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ledger row for a single stock move.
 *
 * <p>{@code docNumber} is resolved from {@code refType} + {@code refId} by the
 * service layer when populating the stock-card view. Supported refTypes:
 * {@code SalesInvoice}, {@code Grn}, {@code CustomerReturn}, {@code VendorReturn},
 * {@code StockCount}, {@code StockTransfer}. Unrecognised refTypes yield null.
 *
 * <p>{@code runningBalance} is computed by the service by accumulating {@code qty}
 * across the current page in chronological order. <b>Limitation:</b> this only
 * reflects the moves on the current page; it is accurate only when the caller
 * paginates from the beginning of the item-branch history (offset 0, sorted
 * oldest-first). Deep-page jumps will carry an incorrect opening balance.
 */
public record StockMoveDto(
    Long id,
    Instant at,
    Long itemId,
    Long branchId,
    Long companyId,
    BigDecimal qty,
    BigDecimal costAmount,
    StockMoveDirection direction,
    StockMoveType moveType,
    String refType,
    Long refId,
    Long actorId,
    String notes,
    Long batchId,
    Long sectionId,
    ConsumptionCategory consumptionCategory,
    Long authorisedByUserId,
    // --- enrichment fields (null when not hydrated) ---
    /** Human-readable document number resolved from refType + refId. */
    String docNumber,
    /** Cumulative running balance after this move on the page (page-relative; see class Javadoc). */
    BigDecimal runningBalance
) {
    /**
     * Thin factory — no enrichment. Used for posting responses and list endpoints
     * where doc-number resolution would be prohibitively expensive.
     */
    public static StockMoveDto from(StockMove move) {
        return new StockMoveDto(
            move.getId(),
            move.getAt(),
            move.getItemId(),
            move.getBranchId(),
            move.getCompanyId(),
            move.getQty(),
            move.getCostAmount(),
            move.getDirection(),
            move.getMoveType(),
            move.getRefType(),
            move.getRefId(),
            move.getActorId(),
            move.getNotes(),
            move.getBatchId(),
            move.getSectionId(),
            move.getConsumptionCategory(),
            move.getAuthorisedByUserId(),
            null,
            null
        );
    }
}
