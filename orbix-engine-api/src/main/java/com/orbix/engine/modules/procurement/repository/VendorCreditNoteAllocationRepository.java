package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.VendorCreditNoteAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorCreditNoteAllocationRepository extends JpaRepository<VendorCreditNoteAllocation, Long> {

    List<VendorCreditNoteAllocation> findByVendorCreditNoteIdOrderByAllocatedAtAsc(Long vendorCreditNoteId);

    List<VendorCreditNoteAllocation> findBySupplierInvoiceIdOrderByAllocatedAtAsc(Long supplierInvoiceId);
}
