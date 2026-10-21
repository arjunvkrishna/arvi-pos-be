package com.neram.pos.config;

import jakarta.servlet.FilterChain;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerLoggingTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest("POST", "/api/test/resource");
        request.setRemoteAddr("192.168.1.100");
        MDC.put(RequestCorrelationFilter.MDC_TRACE_ID, "trc-unit-test-123");
        MDC.put(RequestCorrelationFilter.MDC_USER, "test_admin");
        MDC.put(RequestCorrelationFilter.MDC_CLIENT_IP, "192.168.1.100");
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("handleAccessDenied returns 403 Forbidden with path and traceId")
    void testHandleAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("User lacks permission to delete outlet");
        ResponseEntity<Map<String, Object>> res = handler.handleAccessDenied(ex, request);

        assertEquals(HttpStatus.FORBIDDEN, res.getStatusCode());
        assertEquals(403, res.getBody().get("status"));
        assertEquals("/api/test/resource", res.getBody().get("path"));
        assertEquals("trc-unit-test-123", res.getBody().get("traceId"));
        assertTrue(res.getBody().get("error").toString().contains("lacks permission"));
    }

    @Test
    @DisplayName("handleNoSuchElement returns 404 Not Found")
    void testHandleNoSuchElement() {
        NoSuchElementException ex = new NoSuchElementException("Menu item 999 not found");
        ResponseEntity<Map<String, Object>> res = handler.handleNoSuchElement(ex, request);

        assertEquals(HttpStatus.NOT_FOUND, res.getStatusCode());
        assertEquals(404, res.getBody().get("status"));
        assertEquals("/api/test/resource", res.getBody().get("path"));
        assertEquals("Menu item 999 not found", res.getBody().get("error"));
    }

    @Test
    @DisplayName("handleMethodArgumentNotValid extracts field errors map and returns 400 Bad Request")
    void testHandleMethodArgumentNotValid() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "createCafeRequest");
        bindingResult.addError(new FieldError("createCafeRequest", "name", "Cafe name is required"));
        bindingResult.addError(new FieldError("createCafeRequest", "contactEmail", "Invalid email format"));

        Method method = this.getClass().getDeclaredMethod("setUp");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<Map<String, Object>> res = handler.handleMethodArgumentNotValid(ex, request);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals(400, res.getBody().get("status"));
        assertTrue(res.getBody().get("error").toString().contains("2 field error(s)"));

        @SuppressWarnings("unchecked")
        Map<String, String> fieldErrors = (Map<String, String>) res.getBody().get("fieldErrors");
        assertNotNull(fieldErrors);
        assertEquals("Cafe name is required", fieldErrors.get("name"));
        assertEquals("Invalid email format", fieldErrors.get("contactEmail"));
    }

    @Test
    @DisplayName("handleDataIntegrity sanitizes duplicate key violation (SQLState 23505)")
    void testHandleDataIntegrity_DuplicateKey() {
        String raw = "duplicate key value violates unique constraint 'users_username_key'";
        ConstraintViolationException cve = new ConstraintViolationException(raw, new SQLException("dup", "23505"), "users_username_key");
        DataIntegrityViolationException dive = new DataIntegrityViolationException(raw, cve);

        ResponseEntity<Map<String, Object>> res = handler.handleDataIntegrity(dive, request);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertEquals("A record with the same value already exists.", res.getBody().get("error"));
        assertFalse(res.getBody().get("error").toString().contains("users_username_key"), "No internal DB leaks");
    }

    @Test
    @DisplayName("handleDataIntegrity sanitizes foreign key violation (SQLState 23503)")
    void testHandleDataIntegrity_ForeignKey() {
        ConstraintViolationException cve = new ConstraintViolationException("fk", new SQLException("fk", "23503"), "fk_bills_outlet");
        DataIntegrityViolationException dive = new DataIntegrityViolationException("fk", cve);

        ResponseEntity<Map<String, Object>> res = handler.handleDataIntegrity(dive, request);

        assertEquals(HttpStatus.BAD_REQUEST, res.getStatusCode());
        assertTrue(res.getBody().get("error").toString().contains("linked to other data"));
    }

    @Test
    @DisplayName("handleMethodNotSupported returns 405 Method Not Allowed")
    void testHandleMethodNotSupported() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException(
                "DELETE", List.of("GET", "POST"));
        ResponseEntity<Map<String, Object>> res = handler.handleMethodNotSupported(ex, request);

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, res.getStatusCode());
        assertEquals(405, res.getBody().get("status"));
        assertTrue(res.getBody().get("error").toString().contains("DELETE method is not supported"));
    }

    @Test
    @DisplayName("handleMediaTypeNotSupported returns 415 Unsupported Media Type")
    void testHandleMediaTypeNotSupported() {
        HttpMediaTypeNotSupportedException ex = new HttpMediaTypeNotSupportedException(
                MediaType.APPLICATION_XML, List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Map<String, Object>> res = handler.handleMediaTypeNotSupported(ex, request);

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, res.getStatusCode());
        assertEquals(415, res.getBody().get("status"));
    }

    @Test
    @DisplayName("handleGenericException logs error with traceId and returns 500 without leaking stack trace")
    void testHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected NullPointer inside calculation engine");
        ResponseEntity<Map<String, Object>> res = handler.handleGenericException(ex, request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, res.getStatusCode());
        assertEquals(500, res.getBody().get("status"));
        assertTrue(res.getBody().get("error").toString().contains("internal server error"));
        assertTrue(res.getBody().get("error").toString().contains("trc-unit-test-123"), "Returns trace ID for support");
        assertFalse(res.getBody().get("error").toString().contains("NullPointer"), "No internal stack trace leaked to client");
    }

    @Test
    @DisplayName("RequestCorrelationFilter injects traceId into MDC and sets X-Request-ID response header")
    void testRequestCorrelationFilter() throws Exception {
        RequestCorrelationFilter filter = new RequestCorrelationFilter();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/bills");
        req.addHeader("X-Request-ID", "custom-client-trace-999");
        req.setRemoteAddr("203.0.113.45");

        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = (filterRequest, filterResponse) -> {
            assertEquals("custom-client-trace-999", MDC.get(RequestCorrelationFilter.MDC_TRACE_ID));
            assertEquals("203.0.113.45", MDC.get(RequestCorrelationFilter.MDC_CLIENT_IP));
            assertEquals("GET", MDC.get(RequestCorrelationFilter.MDC_METHOD));
            assertEquals("/api/bills", MDC.get(RequestCorrelationFilter.MDC_URI));
        };

        filter.doFilter(req, res, chain);

        assertEquals("custom-client-trace-999", res.getHeader("X-Request-ID"));
        assertNull(MDC.get(RequestCorrelationFilter.MDC_TRACE_ID), "MDC must be cleared after filter completion");
    }
}
