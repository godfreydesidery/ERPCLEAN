package com.orbix.engine.modules.stock.domain.dto;

/**
 * Optional body for the {@code POST /stock-counts/uid/{uid}/post} endpoint.
 * Under the configured monetary variance threshold no body is required;
 * above the threshold the request MUST name a separate-user authoriser
 * holding {@code STOCK.COUNT_APPROVE} (mirror of the
 * {@code STOCK.ADJUST_APPROVE} dual-control on adjustments).
 *
 * @param authorisedByUserId the user who approves an above-threshold post;
 *                            null is fine for under-threshold posts.
 */
public record PostStockCountRequestDto(Long authorisedByUserId) {

    public static PostStockCountRequestDto empty() {
        return new PostStockCountRequestDto(null);
    }
}
