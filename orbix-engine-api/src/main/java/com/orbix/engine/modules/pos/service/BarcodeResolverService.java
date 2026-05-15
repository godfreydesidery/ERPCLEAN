package com.orbix.engine.modules.pos.service;

import com.orbix.engine.modules.pos.domain.dto.ResolvedBarcodeDto;

/**
 * Resolves a scanned barcode (raw scanner output) to an item + qty for the
 * till (F5.8 / US-CAT-016 / US-POS-003). Plain symbologies match
 * {@code item_barcode.barcode} exactly; scale-printed EAN-13 codes with a
 * leading {@code 2} fall back to a 7-char prefix lookup against barcodes of
 * type {@code EMBEDDED_WEIGHT} and decode the trailing 5 weight digits.
 */
public interface BarcodeResolverService {

    /**
     * @throws java.util.NoSuchElementException if no barcode matches
     * @throws IllegalArgumentException if the code looks like an embedded-weight
     *         scan but the weight digits are zero, or the matched item is archived
     */
    ResolvedBarcodeDto resolve(String scannedCode);
}
