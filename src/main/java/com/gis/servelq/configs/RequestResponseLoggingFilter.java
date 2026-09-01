package com.gis.servelq.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.UUID;

/**
 * Production-grade request/response logging filter.
 * - Skips multipart requests (file uploads) to avoid consuming input streams
 * - Skips static resources and health check endpoints
 * - Masks sensitive headers
 * - Truncates large bodies
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LENGTH = 5000;
    private static final String[] SENSITIVE_HEADERS = {"authorization", "cookie", "set-cookie"};

    // Endpoints to skip logging entirely
    private static final String[] SKIP_URIS = {
            "/static/", "/css/", "/js/", "/images/", "/favicon.ico",
            "/actuator", "/hls/", "/video/stream", "/stream/", "/ws"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Check if this is a multipart request (file upload)
        if (isMultipartRequest(request)) {
            handleMultipartRequest(request, response, filterChain);
            return;
        }

        // Skip logging for certain endpoints
        if (shouldSkipLogging(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request and response for body caching
        CachingRequestWrapper requestWrapper = new CachingRequestWrapper(request);
        CachingResponseWrapper responseWrapper = new CachingResponseWrapper(response);

        String requestId = generateRequestId();
        long startTime = System.currentTimeMillis();

        // Log request
        logRequest(requestWrapper, requestId);

        try {
            // Process the request
            filterChain.doFilter(requestWrapper, responseWrapper);

            // Log response
            logResponse(responseWrapper, requestId, startTime);

        } catch (Exception e) {
            // Log error
            logError(e, requestId, startTime);
            throw e;
        } finally {
            // Copy cached response body back to original response
            copyResponseBody(responseWrapper);
        }
    }

    /**
     * Handle multipart requests without wrapping to avoid stream consumption issues.
     */
    private void handleMultipartRequest(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        log.debug("MULTIPART REQUEST: {} {} (Content-Type: {})",
                request.getMethod(),
                request.getRequestURI(),
                request.getContentType());

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            log.debug("MULTIPART RESPONSE: {} {} - Status: {} - Duration: {}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
        }
    }

    private void logRequest(CachingRequestWrapper request, String requestId) {
        StringBuilder logMessage = new StringBuilder();

        logMessage.append("\n╔══════════════════════════════════════════════════════════════════╗\n");
        logMessage.append("║ REQUEST [ID: ").append(requestId).append("]\n");
        logMessage.append("╠══════════════════════════════════════════════════════════════════╣\n");
        logMessage.append("║ Method: ").append(request.getMethod()).append("\n");
        logMessage.append("║ URI: ").append(request.getRequestURI());

        if (request.getQueryString() != null) {
            logMessage.append("?").append(request.getQueryString());
        }
        logMessage.append("\n");
        logMessage.append("║ Remote Address: ").append(request.getRemoteAddr()).append("\n");
        logMessage.append("║ Content Type: ").append(request.getContentType()).append("\n");

        // Log headers
        logMessage.append("║ Headers:\n");
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames != null && headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = maskSensitiveHeader(headerName, request.getHeader(headerName));
            logMessage.append("║   ").append(headerName).append(": ").append(headerValue).append("\n");
        }

        // Log request body
        logMessage.append("║ Body:\n");
        String body = getRequestBody(request);
        logMessage.append("║   ").append(body).append("\n");

        logMessage.append("╚══════════════════════════════════════════════════════════════════╝");

        log.debug(logMessage.toString());
    }

    private void logResponse(CachingResponseWrapper response, String requestId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        int status = response.getStatus();

        StringBuilder logMessage = new StringBuilder();

        logMessage.append("\n╔══════════════════════════════════════════════════════════════════╗\n");
        logMessage.append("║ RESPONSE [ID: ").append(requestId).append("]\n");
        logMessage.append("╠══════════════════════════════════════════════════════════════════╣\n");
        logMessage.append("║ Status: ").append(status).append("\n");
        logMessage.append("║ Duration: ").append(duration).append("ms\n");

        // Log response headers
        logMessage.append("║ Headers:\n");
        Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            String headerValue = maskSensitiveHeader(headerName, response.getHeader(headerName));
            logMessage.append("║   ").append(headerName).append(": ").append(headerValue).append("\n");
        }

        // Log response body
        logMessage.append("║ Body:\n");
        String body = getResponseBody(response);
        logMessage.append("║   ").append(body).append("\n");

        logMessage.append("╚══════════════════════════════════════════════════════════════════╝");

        // Log based on status code
        if (status >= 500) {
            log.error(logMessage.toString());
        } else if (status >= 400) {
            log.warn(logMessage.toString());
        } else {
            log.debug(logMessage.toString());
        }
    }

    private void logError(Exception e, String requestId, long startTime) {
        long duration = System.currentTimeMillis() - startTime;

        log.error("\n╔══════════════════════════════════════════════════════════════════╗\n" +
                        "║ ERROR [ID: {}]\n" +
                        "╠══════════════════════════════════════════════════════════════════╣\n" +
                        "║ Exception Type: {}\n" +
                        "║ Message: {}\n" +
                        "║ Duration: {}ms\n" +
                        "║ Root Cause: {}\n" +
                        "╚══════════════════════════════════════════════════════════════════╝",
                requestId,
                e.getClass().getName(),
                e.getMessage(),
                duration,
                getRootCauseMessage(e));
    }

    private String getRequestBody(CachingRequestWrapper request) {
        if (request.isMultipartRequest()) {
            return "[MULTIPART CONTENT - NOT LOGGED]";
        }

        if (!request.hasBody()) {
            return "[EMPTY]";
        }

        String body = request.getBodyAsString();
        return truncateBody(body);
    }

    private String getResponseBody(CachingResponseWrapper response) {
        if (!response.hasContent()) {
            return "[EMPTY]";
        }

        // Check if it's binary content
        String contentType = response.getContentType();
        if (contentType != null && isBinaryContent(contentType)) {
            return "[BINARY CONTENT: " + contentType + ", Size: " + response.getContentSize() + " bytes]";
        }

        String body = response.getContentAsString();
        return truncateBody(body);
    }

    private boolean isBinaryContent(String contentType) {
        String lower = contentType.toLowerCase();
        return lower.contains("video") ||
                lower.contains("image") ||
                lower.contains("audio") ||
                lower.contains("octet-stream") ||
                lower.contains("pdf") ||
                lower.contains("zip") ||
                lower.contains("gzip");
    }

    private String truncateBody(String body) {
        if (body.length() > MAX_BODY_LENGTH) {
            return body.substring(0, MAX_BODY_LENGTH) + "... [TRUNCATED - Total: " + body.length() + " chars]";
        }
        return body;
    }

    private String maskSensitiveHeader(String headerName, String headerValue) {
        if (headerValue == null) {
            return "null";
        }

        String lowerHeaderName = headerName.toLowerCase();
        for (String sensitive : SENSITIVE_HEADERS) {
            if (lowerHeaderName.contains(sensitive)) {
                return "******";
            }
        }
        return headerValue;
    }

    private boolean isMultipartRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    private boolean shouldSkipLogging(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Skip configured URIs
        for (String skipUri : SKIP_URIS) {
            if (uri.startsWith(skipUri) || uri.contains(skipUri)) {
                return true;
            }
        }

        return false;
    }

    private void copyResponseBody(CachingResponseWrapper responseWrapper) {
        try {
            responseWrapper.copyBodyToResponse();
        } catch (IOException e) {
            log.warn("Failed to copy response body: {}", e.getMessage());
        }
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String getRootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause.getMessage() != null ? rootCause.getMessage() : "No message";
    }
}