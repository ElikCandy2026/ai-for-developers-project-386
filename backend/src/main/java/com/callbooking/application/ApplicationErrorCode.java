package com.callbooking.application;

/**
 * Closed set of error codes exposed by the API. Mirrors the TypeSpec enums
 * {@code BadRequestCode}, {@code NotFoundCode}, {@code ConflictCode} and
 * {@code InternalErrorCode}; {@code ErrorContractDriftTest} keeps the two sets in
 * sync.
 */
public enum ApplicationErrorCode {

    VALIDATION_FAILED(400),
    MALFORMED_REQUEST(400),
    INVALID_DURATION(400),

    MEETING_TYPE_NOT_FOUND(404),

    SLOT_UNAVAILABLE(409),
    SLOT_TAKEN(409),

    INTERNAL_ERROR(500);

    private final int status;

    ApplicationErrorCode(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
