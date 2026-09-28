package org.emb.accessrequests.error;

import org.emb.accessrequests.config.RequestBodyGuardFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Turns every exception raised while handling a request into the contract's
 * error body. Nothing here ever exposes exception details or stack traces.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Sent to the loser when two reviewers decide the same step at the same moment. */
    public static final String CONCURRENT_DECISION_MESSAGE =
            "This request was just decided by someone else. Please refresh the page.";

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponseBody> handleApi(ApiException ex) {
        return body(ex.status(), ex.code(), ex.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponseBody> handleUnreadable(HttpMessageNotReadableException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof RequestBodyGuardFilter.BodyTooLargeException) {
                return byStatus(HttpStatus.CONTENT_TOO_LARGE);
            }
        }
        return body(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "The request body is missing or is not valid JSON of the expected shape.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponseBody> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                "The value of '" + ex.getName() + "' is not valid.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorResponseBody> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return byStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorResponseBody> handleOptimisticLock(OptimisticLockingFailureException ex) {
        return body(HttpStatus.CONFLICT, ErrorCodes.ALREADY_FINALIZED, CONCURRENT_DECISION_MESSAGE);
    }

    // Security exceptions thrown inside a controller (e.g. by method security) would
    // otherwise be caught by the catch-all below and turned into a 500.
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorResponseBody> handleAccessDenied(AccessDeniedException ex) {
        return byStatus(HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ErrorResponseBody> handleAuthentication(AuthenticationException ex) {
        return byStatus(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Everything else. Spring MVC's own exceptions (404 for unknown routes, 405,
     * missing parameters, ...) carry their status via {@link ErrorResponse}; any
     * other exception is an unexpected failure.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponseBody> handleOther(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse && !errorResponse.getStatusCode().is5xxServerError()) {
            return byStatus(errorResponse.getStatusCode());
        }
        log.error("Unexpected error", ex);
        return byStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static ResponseEntity<ErrorResponseBody> byStatus(HttpStatusCode status) {
        return body(status, ErrorCodes.codeFor(status), ErrorCodes.messageFor(status));
    }

    private static ResponseEntity<ErrorResponseBody> body(HttpStatusCode status, String code, String message) {
        return ResponseEntity.status(status).body(ErrorResponseBody.of(code, message));
    }
}
