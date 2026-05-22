package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockBatch;
import com.orbix.engine.modules.stock.domain.enums.StockBatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    Optional<StockBatch> findByBranchIdAndItemIdAndBatchNo(Long branchId, Long itemId, String batchNo);

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
