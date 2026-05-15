package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.ReceiptAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, Long> {

    List<ReceiptAllocation> findBySalesReceiptId(Long salesReceiptId);

    List<ReceiptAllocation> findBySalesInvoiceId(Long salesInvoiceId);

    void deleteBySalesReceiptId(Long salesReceiptId);
}
