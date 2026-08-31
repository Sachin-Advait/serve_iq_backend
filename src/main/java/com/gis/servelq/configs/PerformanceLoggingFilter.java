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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class PerformanceLoggingFilter extends OncePerRequestFilter {

    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000; // 1 second
    private static final ConcurrentHashMap<String, AtomicLong> requestCounter = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();
        String uri = request.getRequestURI();
        String method = request.getMethod();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            if (duration > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW REQUEST: {} {} took {}ms", method, uri, duration);
            }

            // Track request statistics
            String key = method + " " + uri;
            requestCounter.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

            if (log.isTraceEnabled()) {
                log.trace("Request stats - {}: {} requests", key, requestCounter.get(key).get());
            }
        }
    }
}