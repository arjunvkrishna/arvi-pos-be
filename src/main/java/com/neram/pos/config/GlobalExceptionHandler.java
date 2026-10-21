package com.neram.pos.config;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

import org.springframework.security.access.AccessDeniedException;

import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Access Denied"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", messageOf(e, "Invalid request")));
    }

    /**
     * Database constraint failures: never echo the SQL statement, table or column names back to the client.
     * Status stays 400 (as before); only the message is made safe and readable.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        String sqlState = null;
        Throwable t = e;
        while (t != null) {
            if (t instanceof ConstraintViolationException cve) {
                sqlState = cve.getSQLState();
                break;
            }
            t = t.getCause();
        }
        String message;
        if ("23505".equals(sqlState)) {
            message = "A record with the same value already exists.";
        } else if ("23502".equals(sqlState)) {
            message = "A required field is missing.";
        } else if ("23503".equals(sqlState)) {
            message = "This record is linked to other data and cannot be changed or removed that way.";
        } else {
            message = "The data could not be saved because it conflicts with existing records.";
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
    }

    /** Long-lived SSE streams are rotated on purpose; the response is already committed, so there is nothing to send. */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncTimeout() {
        // intentionally empty
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", messageOf(e, "Request failed")));
    }

    /** Map.of rejects nulls, so exceptions without a message must never reach it unguarded. */
    private static String messageOf(Throwable e, String fallback) {
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? fallback : m;
    }
}
