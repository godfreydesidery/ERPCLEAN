package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    Optional<StockBatch> findByBranchIdAndItemIdAndBatchNo(Long branchId, Long itemId, String batchNo);

    /**
     * Paginated batch search. {@code branchId} / {@code itemId} / {@code status}
     * are each optional (null = no filter). Ordered FEFO — earliest expiry, then
     * id; pass an unsorted {@link Pageable} so this {@code order by} stands.
     */
    @Query(value = """
        select b from StockBatch b
        where b.companyId = :companyId
          and (:branchId is null or b.branchId = :branchId)
          and (:itemId is null or b.itemId = :itemId)
          and (:status is null or b.status = :status)
        order by b.expiryAt asc, b.id asc
        """,
        countQuery = """
        select count(b) from StockBatch b
        where b.companyId = :companyId
          and (:branchId is null or b.branchId = :branchId)
          and (:itemId is null or b.itemId = :itemId)
          and (:status is null or b.status = :status)
        """)
    Page<StockBatch> search(@Param("companyId") Long companyId,
                            @Param("branchId") Long branchId,
                            @Param("itemId") Long itemId,
                            @Param("status") StockBatchStatus status,
                            Pageable pageable);

    /** FEFO order: earliest expiry first, then earliest id; nulls last. */
    List<StockBatch> findByItemIdAndBranchIdAndStatusOrderByExpiryAtAscIdAsc(
        Long itemId, Long branchId, StockBatchStatus status);

    List<StockBatch> findByCompanyIdOrderByExpiryAtAscIdAsc(Long companyId);

    List<StockBatch> findByCompanyIdAndBranchIdOrderByExpiryAtAscIdAsc(Long companyId, Long branchId);

    List<StockBatch> findByCompanyIdAndItemIdOrderByExpiryAtAscIdAsc(Long companyId, Long itemId);

    List<StockBatch> findByCompanyIdAndStatusOrderByExpiryAtAscIdAsc(Long companyId, StockBatchStatus status);

    List<StockBatch> findByStatusAndExpiryAtBefore(StockBatchStatus status, LocalDate cutoff);

    List<StockBatch> findByCompanyIdAndStatusAndExpiryAtBeforeOrderByExpiryAtAscIdAsc(
        Long companyId, StockBatchStatus status, LocalDate cutoff);

    boolean existsByItemIdAndStatus(Long itemId, StockBatchStatus status);
}
