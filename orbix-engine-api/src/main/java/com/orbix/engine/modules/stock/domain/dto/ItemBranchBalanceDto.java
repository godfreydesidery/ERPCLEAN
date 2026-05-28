package com.orbix.engine.modules.stock.domain.dto;

import com.orbix.engine.modules.stock.domain.entity.ItemBranchBalance;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Balance snapshot for one (item, branch) pair. The display fields
 * {@code itemCode}, {@code itemName}, {@code branchName} are hydrated by
 * service-layer bulk lookups; {@code lastMovedAt} comes straight from
 * the balance row.
 */
public record ItemBranchBalanceDto(
    Long itemId,
    Long branchId,
    BigDecimal qtyOnHand,
    BigDecimal qtyReserved,
    BigDecimal qtyInTransit,
    BigDecimal avgCost,
    BigDecimal lastCost,
    BigDecimal reorderMin,
    BigDecimal reorderMax,
    String binLocation,
    Instant lastMovedAt,
    // --- enrichment fields (null when not hydrated) ---
    String itemCode,
    String itemName,
    String branchName
) {
    /**
     * Thin factory used where only the balance entity is at hand (e.g. the
     * {@code listBalances} endpoint). Display fields are left null; use
     * {@link #hydrated(ItemBranchBalance, String, String, String)} for
     * report endpoints.
     */
    public static ItemBranchBalanceDto from(ItemBranchBalance balance) {
        return new ItemBranchBalanceDto(
            balance.getItemId(),
            balance.getBranchId(),
            balance.getQtyOnHand(),
            balance.getQtyReserved(),
            balance.getQtyInTransit(),
            balance.getAvgCost(),
            balance.getLastCost(),
            balance.getReorderMin(),
            balance.getReorderMax(),
            balance.getBinLocation(),
            balance.getLastMovedAt(),
            null,
            null,
            null
        );
    }

    /** Full factory: includes human-readable item code, item name and branch name. */
    public static ItemBranchBalanceDto hydrated(ItemBranchBalance balance,
                                                String itemCode,
                                                String itemName,
                                                String branchName) {
        return new ItemBranchBalanceDto(
            balance.getItemId(),
            balance.getBranchId(),
            balance.getQtyOnHand(),
            balance.getQtyReserved(),
            balance.getQtyInTransit(),
            balance.getAvgCost(),
            balance.getLastCost(),
            balance.getReorderMin(),
            balance.getReorderMax(),
            balance.getBinLocation(),
            balance.getLastMovedAt(),
            itemCode,
            itemName,
            branchName
        );
    }
}
