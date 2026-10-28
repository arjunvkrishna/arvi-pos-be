package com.neram.pos.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class PublicRateLimiterFilter extends OncePerRequestFilter {

    /** Gift-card balance lookups: /api/public/{cafe}/gift-cards/... */
    private static final java.util.regex.Pattern GIFT_CARD_PATH = java.util.regex.Pattern.compile("^/api/public/[^/]+/gift-cards(/.*)?$");
    private static final int CAPACITY = 10;
    private static final Duration REFILL_DURATION = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Bucket> ipBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastAccessed = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanup = new AtomicLong(System.currentTimeMillis());

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (GIFT_CARD_PATH.matcher(path).matches()) {
            String clientIp = resolveClientIp(request);
            cleanupIfNecessary();

            Bucket bucket = ipBuckets.computeIfAbsent(clientIp, k -> createNewBucket());
            lastAccessed.put(clientIp, System.currentTimeMillis());

            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again in a minute.\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private Bucket createNewBucket() {
        Bandwidth limit = Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(CAPACITY, REFILL_DURATION)
                .build();
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        return com.neram.pos.util.ClientIp.of(request);
    }

    private void cleanupIfNecessary() {
        long now = System.currentTimeMillis();
        long last = lastCleanup.get();
        if (now - last > 5 * 60_000L) {
            if (lastCleanup.compareAndSet(last, now)) {
                lastAccessed.entrySet().removeIf(e -> {
                    if (now - e.getValue() > 5 * 60_000L) {
                        ipBuckets.remove(e.getKey());
                        return true;
                    }
                    return false;
                });
            }
        }
    }
}
