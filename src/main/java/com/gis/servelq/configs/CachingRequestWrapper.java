package com.gis.servelq.configs;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper for HttpServletRequest that caches the request body for logging purposes.
 * For multipart requests (file uploads), it delegates to the original request to avoid
 * consuming the input stream.
 */
public class CachingRequestWrapper extends HttpServletRequestWrapper {

    private byte[] body;
    private final Map<String, String[]> parameterMap;

    public CachingRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);

        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");

        if (isMultipart) {
            // For multipart requests, don't cache the body
            // Let Spring handle multipart parsing directly
            this.body = new byte[0];
            this.parameterMap = Collections.unmodifiableMap(new HashMap<>(request.getParameterMap()));
        } else {
            // For non-multipart requests, cache the body
            StringBuilder stringBuilder = new StringBuilder();
            try (BufferedReader bufferedReader = request.getReader()) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append('\n');
                    }
                    stringBuilder.append(line);
                }
            }
            this.body = stringBuilder.toString().getBytes(StandardCharsets.UTF_8);
            this.parameterMap = Collections.unmodifiableMap(new HashMap<>(request.getParameterMap()));
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (isMultipartRequest()) {
            // For multipart, delegate to original request
            return super.getInputStream();
        }

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }

            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Not implemented for synchronous reading
                throw new UnsupportedOperationException("Not supported");
            }
        };
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (isMultipartRequest()) {
            // For multipart, delegate to original request
            return super.getReader();
        }
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        return values != null && values.length > 0 ? values[0] : super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        if (parameterMap.isEmpty()) {
            return super.getParameterMap();
        }
        return parameterMap;
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = parameterMap.get(name);
        return values != null ? values.clone() : super.getParameterValues(name);
    }

    public byte[] getBody() {
        return body.clone();
    }

    public String getBodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public boolean isMultipartRequest() {
        String contentType = getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    public boolean hasBody() {
        return body.length > 0;
    }
}