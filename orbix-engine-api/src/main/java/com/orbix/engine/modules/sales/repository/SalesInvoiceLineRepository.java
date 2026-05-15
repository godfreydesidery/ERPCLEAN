package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {

    List<SalesInvoiceLine> findBySalesInvoiceIdOrderByLineNoAsc(Long salesInvoiceId);

    void deleteBySalesInvoiceId(Long salesInvoiceId);
}
