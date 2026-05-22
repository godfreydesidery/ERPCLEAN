package com.orbix.engine.modules.procurement.repository;

import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoiceGrn;
import com.orbix.engine.modules.procurement.domain.entity.SupplierInvoiceGrnId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SupplierInvoiceGrnRepository
        extends JpaRepository<SupplierInvoiceGrn, SupplierInvoiceGrnId> {

    List<SupplierInvoiceGrn> findBySupplierInvoiceId(Long supplierInvoiceId);

    List<SupplierInvoiceGrn> findByGrnId(Long grnId);

    /**
     * Sum of amounts already allocated to a given GRN by other (non-cancelled) invoices.
     * Excludes the invoice being saved so editing in-place doesn't double-count.
     */
    @Query("""
        select coalesce(sum(a.amount), 0)
          from SupplierInvoiceGrn a
          join SupplierInvoice i on i.id = a.supplierInvoiceId
         where a.grnId = :grnId
           and i.status <> com.orbix.engine.modules.procurement.domain.enums.SupplierInvoiceStatus.CANCELLED
           and (:excludeInvoiceId is null or a.supplierInvoiceId <> :excludeInvoiceId)
        """)
    BigDecimal sumAllocatedToGrn(@Param("grnId") Long grnId,
                                 @Param("excludeInvoiceId") Long excludeInvoiceId);

    void deleteBySupplierInvoiceId(Long supplierInvoiceId);
}
