package com.orbix.engine.modules.common.service;

import com.orbix.engine.modules.common.domain.entity.AuditLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Canonical serialisation + SHA-256 hashing for the audit chain. Shared by the
 * writer (to compute {@code rowHash}) and the integrity checker (to recompute
 * and compare). The canonical form must be byte-stable across writes and reads,
 * so the timestamp is reduced to whole epoch seconds (the column stores second
 * precision) and nulls render as empty strings.
 */
public final class AuditHash {

    /** prev_hash of the very first row — 64 zeros (a non-colliding sentinel). */
    public static final String GENESIS = "0".repeat(64);

    private AuditHash() {}

    public static String canonical(AuditLog r, String prevHash) {
        return String.join("|",
            prevHash,
            Long.toString(r.getAt().getEpochSecond()),
            Long.toString(r.getActorId()),
            nz(r.getAction()),
            nz(r.getEntityType()),
            nz(r.getEntityId()),
            r.getCompanyId() == null ? "" : r.getCompanyId().toString(),
            r.getBranchId() == null ? "" : r.getBranchId().toString(),
            nz(r.getBeforeJson()),
            nz(r.getAfterJson()),
            nz(r.getMetaJson())
        );
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** The expected row_hash for a row given its predecessor's hash. */
    public static String rowHash(AuditLog r, String prevHash) {
        return sha256Hex(canonical(r, prevHash));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
