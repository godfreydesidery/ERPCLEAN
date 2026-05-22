package com.orbix.engine.modules.stock.repository;

import com.orbix.engine.modules.stock.domain.entity.StockTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {

    List<StockTransfer> findByCompanyIdOrderByIdDesc(Long companyId);

    @Query("select t from StockTransfer t where t.companyId = :companyId "
        + "and (t.fromBranchId = :branchId or t.toBranchId = :branchId) "
        + "order by t.id desc")
    List<StockTransfer> findInvolvingBranch(@Param("companyId") Long companyId,
                                            @Param("branchId") Long branchId);

    boolean existsByCompanyIdAndNumber(Long companyId, String number);
}
