package org.emb.accessrequests.error;

import org.springframework.http.HttpStatusCode;

/** Stable error codes from the API contract, plus a default code and message per HTTP status. */
public final class ErrorCodes {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String DUPLICATE_REQUEST = "DUPLICATE_REQUEST";
    public static final String ALREADY_FINALIZED = "ALREADY_FINALIZED";
    public static final String NOT_YOUR_STEP = "NOT_YOUR_STEP";
    public static final String UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Not in the contract's table; used for protocol-level errors the UI never triggers.
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String BAD_REQUEST = "BAD_REQUEST";

    public static final String UNAUTHENTICATED_MESSAGE = "Your session has expired. Please log in again.";
    public static final String FORBIDDEN_MESSAGE = "You do not have permission to do that.";
    public static final String INTERNAL_ERROR_MESSAGE = "Something went wrong. Please try again.";

    private ErrorCodes() {
    }

    public static String codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> VALIDATION_ERROR;
            case 401 -> UNAUTHENTICATED;
            case 403 -> FORBIDDEN;
            case 404 -> NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 429 -> RATE_LIMITED;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }

    public static String messageFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "The request is invalid.";
            case 401 -> UNAUTHENTICATED_MESSAGE;
            case 403 -> FORBIDDEN_MESSAGE;
            case 404 -> "The requested resource was not found.";
            case 405 -> "This method is not allowed for this resource.";
            case 413 -> "The request body is too large.";
            case 415 -> "The request body must be JSON (Content-Type: application/json).";
            case 429 -> "Too many requests. Please try again later.";
            default -> status.is4xxClientError() ? "The request could not be processed." : INTERNAL_ERROR_MESSAGE;
        };
    }
}
