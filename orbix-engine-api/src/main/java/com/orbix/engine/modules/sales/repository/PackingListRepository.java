package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.PackingList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PackingListRepository extends JpaRepository<PackingList, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<PackingList> findByUid(String uid);

    List<PackingList> findByCompanyIdOrderByIdDesc(Long companyId);

    List<PackingList> findByCompanyIdAndBranchIdOrderByIdDesc(Long companyId, Long branchId);

    List<PackingList> findBySalesInvoiceIdOrderByIdDesc(Long salesInvoiceId);
}
