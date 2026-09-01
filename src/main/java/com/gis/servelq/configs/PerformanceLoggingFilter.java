package com.gis.servelq.configs;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-grade performance monitoring filter.
 * Tracks request durations and identifies slow requests.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PerformanceLoggingFilter extends OncePerRequestFilter {

    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000; // 1 second
    private static final long VERY_SLOW_REQUEST_THRESHOLD_MS = 5000; // 5 seconds
    private static final ConcurrentHashMap<String, AtomicLong> requestCounter = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String uri = request.getRequestURI();
        String method = request.getMethod();
        boolean isMultipart = isMultipartRequest(request);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            // Log slow requests
            if (duration > VERY_SLOW_REQUEST_THRESHOLD_MS) {
                log.error("VERY SLOW REQUEST: {} {} took {}ms (Status: {})",
                        method, uri, duration, response.getStatus());
            } else if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW REQUEST: {} {} took {}ms (Status: {})",
                        method, uri, duration, response.getStatus());
            }

            // Track request statistics
            String key = method + " " + uri;
            AtomicLong counter = requestCounter.computeIfAbsent(key, k -> new AtomicLong(0));
            long count = counter.incrementAndGet();

            // Log statistics periodically (every 1000 requests)
            if (count % 1000 == 0) {
                log.info("Request stats - {}: {} requests", key, count);
            }

            // Log multipart requests separately
            if (isMultipart && log.isDebugEnabled()) {
                log.debug("MULTIPART: {} {} - {}ms (Size: {} bytes)",
                        method, uri, duration, request.getContentLengthLong());
            }
        }
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }
}