package com.orbix.engine.modules.cash.repository;

import com.orbix.engine.modules.cash.domain.entity.SupplierPaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupplierPaymentAllocationRepository
        extends JpaRepository<SupplierPaymentAllocation, Long> {

    List<SupplierPaymentAllocation> findBySupplierPaymentId(Long supplierPaymentId);

    List<SupplierPaymentAllocation> findBySupplierInvoiceId(Long supplierInvoiceId);

    void deleteBySupplierPaymentId(Long supplierPaymentId);
}
