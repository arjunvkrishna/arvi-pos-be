package com.neram.pos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.*;

/**
 * Enterprise Global Exception Handler for ARVI POS Platform.
 * Provides unified, secure, structured error responses with correlation trace IDs
 * and rich contextual logging across all log levels (INFO, WARN, ERROR, DEBUG, TRACE).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ------------------------------------------------------------------------
    // 1. Security & Authentication Failures (403, 401)
    // ------------------------------------------------------------------------

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        String msg = messageOf(e, "Access Denied: You do not have sufficient permissions to access this resource");
        log.warn("Access Denied [user={}, method={}, path={}, traceId={}]: {}",
                getUser(), getMethod(request), getPath(request), getTraceId(), msg);
        return buildResponse(HttpStatus.FORBIDDEN, msg, request, null);
    }

    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException e) {
        return handleAccessDenied(e, null);
    }

    @ExceptionHandler(com.neram.pos.tenancy.TenantNotSelectedException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNotSelected(
            com.neram.pos.tenancy.TenantNotSelectedException e, HttpServletRequest request) {
        log.warn("No cafe selected [method={}, path={}, traceId={}]", getMethod(request), getPath(request), getTraceId());
        return buildResponse(HttpStatus.BAD_REQUEST,
                "This request needs a cafe. Log in with a cafe account or name the cafe in the request.", request, null);
    }

    @ExceptionHandler(com.neram.pos.tenancy.TenantUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleTenantUnavailable(
            com.neram.pos.tenancy.TenantUnavailableException e, HttpServletRequest request) {
        log.warn("Cafe unavailable [method={}, path={}, traceId={}]: {}", getMethod(request), getPath(request),
                getTraceId(), e.getMessage());
        return buildResponse(HttpStatus.valueOf(e.getHttpStatus()), e.getMessage(), request, null);
    }

    // ------------------------------------------------------------------------
    // 2. Resource Not Found (404)
    // ------------------------------------------------------------------------

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNoSuchElement(NoSuchElementException e, HttpServletRequest request) {
        String msg = messageOf(e, "Requested resource not found");
        log.info("Resource Not Found [method={}, path={}, traceId={}]: {}",
                getMethod(request), getPath(request), getTraceId(), msg);
        return buildResponse(HttpStatus.NOT_FOUND, msg, request, null);
    }

    public ResponseEntity<Map<String, Object>> handleNoSuchElement(NoSuchElementException e) {
        return handleNoSuchElement(e, null);
    }

    // ------------------------------------------------------------------------
    // 3. Validation Failures (400)
    // ------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        BindingResult bindingResult = e.getBindingResult();
        for (FieldError fieldError : bindingResult.getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        String summary = String.format("Validation failed: %d field error(s)", fieldErrors.size());
        log.warn("Validation failure [method={}, path={}, traceId={}]: {}",
                getMethod(request), getPath(request), getTraceId(), fieldErrors);

        return buildResponse(HttpStatus.BAD_REQUEST, summary, request, fieldErrors);
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleJakartaConstraintViolation(
            jakarta.validation.ConstraintViolationException e, HttpServletRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }

        log.warn("Constraint violation [method={}, path={}, traceId={}]: {}",
                getMethod(request), getPath(request), getTraceId(), fieldErrors);

        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid input parameters", request, fieldErrors);
    }

    // ------------------------------------------------------------------------
    // 4. Client Request Errors (400, 405, 415)
    // ------------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e, HttpServletRequest request) {
        String msg = messageOf(e, "Invalid request parameters");
        log.warn("Bad Request [method={}, path={}, traceId={}]: {}",
                getMethod(request), getPath(request), getTraceId(), msg);
        return buildResponse(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return handleIllegalArgument(e, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e, HttpServletRequest request) {
        String msg = messageOf(e, "Operation cannot be performed in current state");
        log.warn("Illegal State [method={}, path={}, traceId={}]: {}",
                getMethod(request), getPath(request), getTraceId(), msg);
        return buildResponse(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e, HttpServletRequest request) {
        String msg = String.format("Required query parameter '%s' of type '%s' is missing", e.getParameterName(), e.getParameterType());
        log.warn("Missing Request Parameter [method={}, path={}]: {}", getMethod(request), getPath(request), msg);
        return buildResponse(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String msg = String.format("Parameter '%s' has invalid value: '%s'", e.getName(), e.getValue());
        log.warn("Parameter Type Mismatch [method={}, path={}]: {}", getMethod(request), getPath(request), msg);
        return buildResponse(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    /** An unknown URL (a typo, a bot probing) is a plain 404, not a "critical unhandled error". */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(org.springframework.web.servlet.resource.NoResourceFoundException e, HttpServletRequest request) {
        log.debug("No resource [method={}, path={}]", getMethod(request), getPath(request));
        return buildResponse(HttpStatus.NOT_FOUND, "Not found", request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String msg = String.format("HTTP %s method is not supported for this endpoint. Supported: %s",
                e.getMethod(), Arrays.toString(e.getSupportedMethods()));
        log.info("Method Not Supported [method={}, path={}]: {}", getMethod(request), getPath(request), msg);
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, msg, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String msg = String.format("Content-Type '%s' is not supported. Supported: %s",
                e.getContentType(), e.getSupportedMediaTypes());
        log.info("Media Type Not Supported [method={}, path={}]: {}", getMethod(request), getPath(request), msg);
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, msg, request, null);
    }

    // ------------------------------------------------------------------------
    // 5. Database Constraint Failures (Safe sanitization, no SQL leaks)
    // ------------------------------------------------------------------------

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
        String sqlState = extractSqlState(e);
        String causeMsg = e.getMostSpecificCause() != null ? e.getMostSpecificCause().getMessage() : "unknown";

        log.warn("Data integrity violation [method={}, path={}, traceId={}, SQLState={}]: {}",
                getMethod(request), getPath(request), getTraceId(), sqlState, causeMsg);

        String message;
        if ("23505".equals(sqlState)) {
            message = "A record with the same value already exists.";
        } else if ("23502".equals(sqlState)) {
            message = "A required field is missing.";
        } else if ("23503".equals(sqlState)) {
            message = "This record is linked to other data and cannot be changed or removed that way.";
        } else if ("23P01".equals(sqlState) || causeMsg.contains("no_overlapping_shifts")) {
            message = "Employee has an overlapping shift scheduled.";
        } else {
            message = "The data could not be saved because it conflicts with existing records.";
        }

        return buildResponse(HttpStatus.BAD_REQUEST, message, request, null);
    }

    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException e) {
        return handleDataIntegrity(e, null);
    }

    // ------------------------------------------------------------------------
    // 6. SSE Stream Timeout (Async Lifecycle)
    // ------------------------------------------------------------------------

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public void handleAsyncTimeout(HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("SSE async request stream timeout [path={}, traceId={}]", getPath(request), getTraceId());
        }
    }

    /** The browser closed a live-updates connection (tab closed, page changed): nobody is left to answer and nothing is wrong. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientGone(HttpServletRequest request) {
        if (log.isDebugEnabled()) {
            log.debug("Live-updates client disconnected [path={}]", getPath(request));
        }
    }

    // ------------------------------------------------------------------------
    // 7. General Runtime & Unexpected Server Exceptions (500)
    // ------------------------------------------------------------------------

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String msg = messageOf(e, "Request processing failed");
        log.warn("Request failed with RuntimeException [method={}, path={}, traceId={}, user={}]: {}",
                getMethod(request), getPath(request), getTraceId(), getUser(), msg);

        if (log.isDebugEnabled()) {
            log.debug("Diagnostic stack trace for RuntimeException:", e);
        }

        return buildResponse(HttpStatus.BAD_REQUEST, msg, request, null);
    }

    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException e) {
        return handleRuntimeException(e, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception e, HttpServletRequest request) {
        String traceId = getTraceId();
        log.error("CRITICAL UNHANDLED ERROR [method={}, path={}, traceId={}, user={}]: {}",
                getMethod(request), getPath(request), traceId, getUser(), e.getMessage(), e);

        String safeMessage = "An unexpected internal server error occurred. Reference error trace ID: " + traceId;
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, safeMessage, request, null);
    }

    // ------------------------------------------------------------------------
    // Helper Methods
    // ------------------------------------------------------------------------

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, String message, HttpServletRequest request, Map<String, String> fieldErrors) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", message);
        body.put("message", message);
        body.put("status", status.value());
        body.put("timestamp", Instant.now().toString());
        body.put("path", getPath(request));
        body.put("traceId", getTraceId());

        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            body.put("fieldErrors", fieldErrors);
        }

        return ResponseEntity.status(status).body(body);
    }

    private static String messageOf(Throwable e, String fallback) {
        if (e == null) return fallback;
        String m = e.getMessage();
        return (m == null || m.isBlank()) ? fallback : m;
    }

    private static String extractSqlState(DataIntegrityViolationException e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ConstraintViolationException cve) {
                return cve.getSQLState();
            }
            t = t.getCause();
        }
        return null;
    }

    private static String getTraceId() {
        String traceId = MDC.get(RequestCorrelationFilter.MDC_TRACE_ID);
        return traceId != null ? traceId : "SYS-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String getUser() {
        String user = MDC.get(RequestCorrelationFilter.MDC_USER);
        if (user != null && !user.isBlank()) return user;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
    }

    private static String getMethod(HttpServletRequest request) {
        if (request != null) return request.getMethod();
        String mdcMethod = MDC.get(RequestCorrelationFilter.MDC_METHOD);
        return mdcMethod != null ? mdcMethod : "N/A";
    }

    private static String getPath(HttpServletRequest request) {
        if (request != null) return request.getRequestURI();
        String mdcUri = MDC.get(RequestCorrelationFilter.MDC_URI);
        return mdcUri != null ? mdcUri : "N/A";
    }
}
