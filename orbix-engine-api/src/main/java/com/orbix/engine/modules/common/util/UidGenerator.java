package com.orbix.engine.modules.common.util;

import com.github.f4b6a3.ulid.UlidCreator;

/**
 * ULID generation + validation for externally-exposed entity UIDs. Wraps
 * f4b6a3's ulid-creator so we have a single point to swap implementations.
 *
 * <p>{@link #next()} returns a monotonic ULID — within the same millisecond
 * the lexicographic order matches insertion order, so primary-key-style
 * scans stay sorted.
 */
public final class UidGenerator {

    private UidGenerator() { /* utility */ }

    /** New monotonic ULID — 26-char Crockford base32. */
    public static String next() {
        return UlidCreator.getMonotonicUlid().toString();
    }

    /**
     * True iff {@code s} looks like a ULID: 26 chars, all in the Crockford
     * base32 alphabet (0-9, A-Z minus I, L, O, U; case-insensitive).
     */
    public static boolean isValid(String s) {
        if (s == null || s.length() != 26) {
            return false;
        }
        for (int i = 0; i < 26; i++) {
            if (!isCrockfordChar(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCrockfordChar(char c) {
        if (c >= '0' && c <= '9') return true;
        if (c >= 'A' && c <= 'Z') return c != 'I' && c != 'L' && c != 'O' && c != 'U';
        if (c >= 'a' && c <= 'z') return c != 'i' && c != 'l' && c != 'o' && c != 'u';
        return false;
    }
}
