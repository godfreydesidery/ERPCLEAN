package com.orbix.engine.modules.catalog.domain.enums;

/**
 * Barcode symbology / encoding. EMBEDDED_WEIGHT and PLU carry weight or
 * price-lookup data parsed at the till; the rest are plain identifiers.
 */
public enum BarcodeType {
    UPC, EAN13, EAN8, PLU, EMBEDDED_WEIGHT, EMBEDDED_PRICE;

    /** True for symbologies that satisfy the weighed-item barcode requirement. */
    public boolean isWeighedCapable() {
        return this == PLU || this == EMBEDDED_WEIGHT;
    }
}
