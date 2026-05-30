package com.orbix.engine.modules.catalog.repository;

import com.orbix.engine.modules.catalog.domain.entity.PriceListItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PriceListItemRepository extends JpaRepository<PriceListItem, Long> {

    /** Rows with an open end. Retained for the POS catalog snapshot. */
    List<PriceListItem> findByPriceListIdAndValidToIsNull(Long priceListId);

    /**
     * Every price row effective on {@code asOf} — i.e. whose validity window
     * contains the date. By the append-forward invariant at most one row per
     * (item, UoM, tier) is effective on any given date.
     */
    @Query("""
        select p from PriceListItem p
        where p.priceListId = :listId
          and p.validFrom <= :asOf
          and (p.validTo is null or p.validTo >= :asOf)
        """)
    List<PriceListItem> findEffective(@Param("listId") Long listId, @Param("asOf") LocalDate asOf);

    /**
     * Quantity tiers of one item + UoM effective on {@code asOf} that apply to
     * {@code qty} (minQty &le; qty), best tier first. The caller takes the head.
     */
    @Query("""
        select p from PriceListItem p
        where p.priceListId = :listId and p.itemId = :itemId and p.uomId = :uomId
          and p.minQty <= :qty
          and p.validFrom <= :asOf
          and (p.validTo is null or p.validTo >= :asOf)
        order by p.minQty desc
        """)
    List<PriceListItem> findEffectiveTiers(@Param("listId") Long listId, @Param("itemId") Long itemId,
                                           @Param("uomId") Long uomId, @Param("qty") BigDecimal qty,
                                           @Param("asOf") LocalDate asOf);

    /** The most recent row for an exact (item, UoM, tier) tuple — open or closed. */
    Optional<PriceListItem> findFirstByPriceListIdAndItemIdAndUomIdAndMinQtyOrderByValidFromDesc(
        Long priceListId, Long itemId, Long uomId, BigDecimal minQty);

    boolean existsByPriceListId(Long priceListId);

    /** Sync pull delta: price rows whose change_seq is above the cursor watermark.
     *  Scoped to a single price list so each pull request can target the till's list. */
    @Query("select p from PriceListItem p where p.priceListId = :priceListId and p.changeSeq > :cursor order by p.changeSeq asc")
    List<PriceListItem> findByPriceListIdAndChangeSeqGreaterThan(@Param("priceListId") Long priceListId,
                                                                  @Param("cursor") Long cursor,
                                                                  Pageable pageable);
}
