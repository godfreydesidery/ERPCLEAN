package com.orbix.engine.modules.pos.domain.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Base64;

/**
 * Internal representation of the opaque sync cursor token.
 * Wire format: base64(JSON {@code {"v":1,"seq":N}}).
 * The client stores and replays the token verbatim — it never parses it.
 * Adding per-dataset cursors later is additive (new fields, zero client change).
 * Design: docs/design/slice-sync-spine.md §3.3.
 */
public record SyncCursorDto(
    @JsonProperty("v") int version,
    @JsonProperty("seq") long seq
) {
    private static final int CURRENT_VERSION = 1;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final SyncCursorDto ZERO = new SyncCursorDto(CURRENT_VERSION, 0L);

    /** Encode to the opaque token the client stores. */
    public String encode() {
        try {
            String json = MAPPER.writeValueAsString(this);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode sync cursor", e);
        }
    }

    /**
     * Decode a client-supplied opaque token. Returns {@link #ZERO} when
     * {@code token} is null or blank (first pull / bootstrap).
     *
     * @throws IllegalArgumentException if the token is present but malformed.
     */
    public static SyncCursorDto decode(String token) {
        if (token == null || token.isBlank()) {
            return ZERO;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token.trim());
            return MAPPER.readValue(bytes, SyncCursorDto.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid sync cursor token", e);
        }
    }
}
