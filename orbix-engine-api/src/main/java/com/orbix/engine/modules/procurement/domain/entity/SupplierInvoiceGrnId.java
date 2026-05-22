package com.orbix.engine.modules.procurement.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key for {@link SupplierInvoiceGrn}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SupplierInvoiceGrnId implements Serializable {
    private Long supplierInvoiceId;
    private Long grnId;
}
