package com.orbix.engine.modules.common.domain.enums;

/**
 * Business-level response codes carried in {@code ApiResponseDto.responseCode}.
 * String constants rather than an enum so other modules can extend the set
 * without touching this file.
 *
 * <p>Convention: {@code MODULE_ACTION_OUTCOME}, all-caps, underscore-separated.
 */
public final class ResponseCode {

    private ResponseCode() {}

    // ----- generic ---------------------------------------------------------
    public static final String SUCCESS                  = "SUCCESS";
    public static final String CREATED                  = "CREATED";
    public static final String VALIDATION_FAILED        = "VALIDATION_FAILED";
    public static final String NOT_FOUND                = "NOT_FOUND";
    public static final String CONFLICT                 = "CONFLICT";
    public static final String UNAUTHORIZED             = "UNAUTHORIZED";
    public static final String FORBIDDEN                = "FORBIDDEN";
    public static final String INTERNAL_ERROR           = "INTERNAL_ERROR";
    public static final String BAD_REQUEST              = "BAD_REQUEST";

    // ----- auth ------------------------------------------------------------
    public static final String AUTH_LOGIN_OK            = "AUTH_LOGIN_OK";
    public static final String AUTH_INVALID_CREDENTIALS = "AUTH_INVALID_CREDENTIALS";

    // ----- setup -----------------------------------------------------------
    public static final String SETUP_FIRST_RUN_OK       = "SETUP_FIRST_RUN_OK";
    public static final String SETUP_ALREADY_BOOTSTRAPPED = "SETUP_ALREADY_BOOTSTRAPPED";
    public static final String SETUP_STATUS_OK          = "SETUP_STATUS_OK";

    // ----- catalog ---------------------------------------------------------
    public static final String CATALOG_ITEM_CREATED     = "CATALOG_ITEM_CREATED";

    // ----- day (F7.5 EOD) --------------------------------------------------
    public static final String DAY_EOD_BLOCKED          = "DAY_EOD_BLOCKED";
    public static final String BUSINESS_DAY_CLOSED      = "BUSINESS_DAY_CLOSED";

    // ----- state-machine violations ----------------------------------------
    public static final String WRONG_STATE              = "WRONG_STATE";
    public static final String PRECONDITION_FAILED      = "PRECONDITION_FAILED";

    // ----- sync (offline POS) -----------------------------------------------
    /** Client's contract version is below the server minimum — client must upgrade. */
    public static final String SYNC_CONTRACT_TOO_OLD    = "SYNC_CONTRACT_TOO_OLD";
    /** Client's contract version is newer than the server — server must upgrade. */
    public static final String SYNC_CONTRACT_TOO_NEW    = "SYNC_CONTRACT_TOO_NEW";
}
