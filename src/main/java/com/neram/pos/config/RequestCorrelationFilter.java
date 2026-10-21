package com.neram.pos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that establishes request correlation tracking across the application.
 * Injects traceId, client IP, HTTP method, and URI into SLF4J MDC context for structured logging.
 * Returns the correlation traceId in the response header (X-Request-ID).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Request-ID";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_CLIENT_IP = "clientIp";
    public static final String MDC_METHOD = "method";
    public static final String MDC_URI = "uri";
    public static final String MDC_USER = "user";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!StringUtils.hasText(traceId)) {
            traceId = request.getHeader(CORRELATION_ID_HEADER);
        }
        if (!StringUtils.hasText(traceId)) {
            traceId = "trc-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        }

        String clientIp = resolveClientIp(request);

        MDC.put(MDC_TRACE_ID, traceId);
        MDC.put(MDC_CLIENT_IP, clientIp);
        MDC.put(MDC_METHOD, request.getMethod());
        MDC.put(MDC_URI, request.getRequestURI());

        // Initial default user state before JWT filter resolves authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
            MDC.put(MDC_USER, auth.getName());
        } else {
            MDC.put(MDC_USER, "anonymous");
        }

        response.setHeader(TRACE_ID_HEADER, traceId);

        if (log.isTraceEnabled()) {
            log.trace("--> Incoming HTTP request [{} {}] from IP={}", request.getMethod(), request.getRequestURI(), clientIp);
        } else if (log.isDebugEnabled()) {
            log.debug("HTTP {} {}", request.getMethod(), request.getRequestURI());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            if (log.isTraceEnabled()) {
                log.trace("<-- Completed HTTP request [{} {}] with status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
            }
            MDC.clear();
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }
}
