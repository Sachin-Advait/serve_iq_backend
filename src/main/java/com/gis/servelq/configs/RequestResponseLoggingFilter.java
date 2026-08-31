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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_LENGTH = 5000;
    private static final String[] SENSITIVE_HEADERS = {"authorization", "cookie", "set-cookie"};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Skip logging for certain endpoints
        if (shouldSkipLogging(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Create wrappers
        CachingRequestWrapper requestWrapper = new CachingRequestWrapper(request);
        CachingResponseWrapper responseWrapper = new CachingResponseWrapper(response);

        String requestId = generateRequestId();
        long startTime = System.currentTimeMillis();

        // Log request details
        logRequest(requestWrapper, requestId);

        try {
            // Process the request
            filterChain.doFilter(requestWrapper, responseWrapper);

            // Log response details
            logResponse(responseWrapper, requestId, startTime);

        } catch (Exception e) {
            // Log error details
            logError(e, requestId, startTime);
            throw e;
        } finally {
            // Copy response body back to original response
            responseWrapper.copyBodyToResponse();
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
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = maskSensitiveHeader(headerName, request.getHeader(headerName));
            logMessage.append("║   ").append(headerName).append(": ").append(headerValue).append("\n");
        }

        // Log request body
        logMessage.append("║ Body:\n");
        if (request.isMultipartRequest()) {
            logMessage.append("║   [MULTIPART CONTENT - NOT LOGGED]\n");
        } else {
            String body = getRequestBody(request);
            logMessage.append("║   ").append(body).append("\n");
        }

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
                getRootCauseMessage(e),
                e);
    }

    private String getRequestBody(CachingRequestWrapper request) {
        byte[] content = request.getBody();
        if (content.length == 0) {
            return "[EMPTY]";
        }

        String body = new String(content, StandardCharsets.UTF_8);
        return truncateBody(body);
    }

    private String getResponseBody(CachingResponseWrapper response) {
        byte[] content = response.getContent();
        if (content.length == 0) {
            return "[EMPTY]";
        }

        // Check if it's binary content
        String contentType = response.getContentType();
        if (contentType != null &&
                (contentType.contains("video") ||
                        contentType.contains("image") ||
                        contentType.contains("audio") ||
                        contentType.contains("octet-stream"))) {
            return "[BINARY CONTENT: " + contentType + ", Size: " + content.length + " bytes]";
        }

        String body = new String(content, StandardCharsets.UTF_8);
        return truncateBody(body);
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

    private boolean shouldSkipLogging(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Skip static resources
        if (uri.startsWith("/static/") ||
                uri.startsWith("/css/") ||
                uri.startsWith("/js/") ||
                uri.startsWith("/images/") ||
                uri.startsWith("/favicon.ico") ||
                uri.startsWith("/actuator")) {
            return true;
        }

        // Skip OPTIONS requests (CORS preflight)
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Skip video streaming endpoints if too noisy
        if (uri.contains("/hls/") || uri.contains("/video/stream") || uri.contains("/stream/")) {
            return true;
        }

        // Skip WebSocket endpoints
        if (uri.contains("/ws")) {
            return true;
        }

        return false;
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