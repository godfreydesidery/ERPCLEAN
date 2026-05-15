package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCreditNoteRepository extends JpaRepository<CustomerCreditNote, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    List<CustomerCreditNote> findByCustomerReturnId(Long customerReturnId);

    List<CustomerCreditNote> findByCompanyIdOrderByIdDesc(Long companyId);
}
