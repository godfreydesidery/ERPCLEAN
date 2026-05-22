package com.orbix.engine.modules.pos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decodes scale-printed EAN-13 codes carrying an embedded weight (F5.8 / US-CAT-016).
 *
 * <p>Layout (per catalog README §11):
 * <pre>
 *   2 P P P P P P W W W W W C
 *   ^ -------- ^ --------- ^
 *   | 6-digit  | 5-digit   |
 *   | PLU      | weight    | EAN-13 check digit
 *   prefix
 * </pre>
 *
 * <p>The 7-char prefix (leading {@code 2} + 6-digit PLU) is what we store in
 * {@code item_barcode.barcode} when {@code barcode_type = EMBEDDED_WEIGHT};
 * weight digits divide by 1000 → 3 implicit decimals in the item's
 * {@link com.orbix.engine.modules.catalog.domain.enums.WeighingUnit}. The
 * EAN-13 check digit is not re-verified here — the scanner already enforces
 * it, and the till app should reject a bad scan before posting.
 */
final class EmbeddedWeightDecoder {

    private static final int LENGTH = 13;
    private static final int PREFIX_LEN = 7;
    private static final int WEIGHT_START = 7;
    private static final int WEIGHT_END = 12;
    private static final BigDecimal THOUSAND = new BigDecimal("1000");

    private EmbeddedWeightDecoder() {}

    /** True iff {@code code} is 13 digits starting with {@code 2}. */
    static boolean isCandidate(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        if (code.charAt(0) != '2') {
            return false;
        }
        for (int i = 0; i < LENGTH; i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Bytes 1..7 of a candidate code: {@code '2'} + 6-digit PLU. */
    static String prefix(String code) {
        return code.substring(0, PREFIX_LEN);
    }

    /** Bytes 8..12 of a candidate code, scaled by 1/1000 — qty in the item's WeighingUnit. */
    static BigDecimal decodedWeight(String code) {
        int raw = Integer.parseInt(code.substring(WEIGHT_START, WEIGHT_END));
        return new BigDecimal(raw).divide(THOUSAND, 3, RoundingMode.UNNECESSARY);
    }
}
