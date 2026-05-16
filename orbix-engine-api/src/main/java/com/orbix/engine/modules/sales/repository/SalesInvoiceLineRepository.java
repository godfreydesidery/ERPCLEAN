package com.orbix.engine.modules.sales.repository;

import com.orbix.engine.modules.sales.domain.entity.SalesInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SalesInvoiceLineRepository extends JpaRepository<SalesInvoiceLine, Long> {

    List<SalesInvoiceLine> findBySalesInvoiceIdOrderByLineNoAsc(Long salesInvoiceId);

    void deleteBySalesInvoiceId(Long salesInvoiceId);

    /**
     * F8.8 / US-NFR-COMP-001 — per-VAT-group output rollup over a
     * {@code [from, to]} window driven by {@code sales_invoice.posted_business_date}.
     * VOIDED + DRAFT + CANCELLED excluded — they never crystallised revenue.
     * Returns {@code Object[]{vatGroupId, sumNet, sumTax}} where
     * {@code net = line_total − tax_amount} (sales lines store
     * {@code line_total} tax-inclusive).
     */
    @Query("""
        select l.vatGroupId,
               coalesce(sum(l.lineTotal - l.taxAmount), 0),
               coalesce(sum(l.taxAmount), 0)
          from SalesInvoiceLine l join SalesInvoice s on l.salesInvoiceId = s.id
         where s.companyId = :companyId
           and (:branchId is null or s.branchId = :branchId)
           and s.postedBusinessDate between :from and :to
           and s.status in (
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.POSTED,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PARTIALLY_PAID,
              com.orbix.engine.modules.sales.domain.enums.SalesInvoiceStatus.PAID)
         group by l.vatGroupId
        """)
    List<Object[]> aggregateOutputVat(@Param("companyId") Long companyId,
                                      @Param("branchId") Long branchId,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
