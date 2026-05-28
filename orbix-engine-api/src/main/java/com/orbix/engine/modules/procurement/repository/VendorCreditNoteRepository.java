package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorCreditNoteRepository extends JpaRepository<VendorCreditNote, Long> {

    boolean existsByBranchIdAndNumber(Long branchId, String number);

    Optional<VendorCreditNote> findByUid(String uid);

    List<VendorCreditNote> findByVendorReturnId(Long vendorReturnId);

    List<VendorCreditNote> findByCompanyIdOrderByIdDesc(Long companyId);
}
