package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.CustomerCreditNoteAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerCreditNoteAllocationRepository extends JpaRepository<CustomerCreditNoteAllocation, Long> {

    List<CustomerCreditNoteAllocation> findByCustomerCreditNoteIdOrderByAllocatedAtAsc(Long customerCreditNoteId);

    List<CustomerCreditNoteAllocation> findBySalesInvoiceIdOrderByAllocatedAtAsc(Long salesInvoiceId);
}
