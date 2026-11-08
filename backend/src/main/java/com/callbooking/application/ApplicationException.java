package com.callbooking.application;

/**
 * Failure that the API layer turns into a typed error response. The HTTP status
 * is derived from {@link ApplicationErrorCode}, so a code can never be returned
 * with the wrong status.
 */
public class ApplicationException extends RuntimeException {

    private final ApplicationErrorCode code;

    public ApplicationException(ApplicationErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ApplicationErrorCode code() {
        return code;
    }

    public int status() {
        return code.status();
    }

    public static ApplicationException of(ApplicationErrorCode code, String message) {
        return new ApplicationException(code, message);
    }
}
