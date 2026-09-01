package com.gis.servelq.configs;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * A wrapper for HttpServletResponse that caches the response body for logging purposes.
 */
public class CachingResponseWrapper extends HttpServletResponseWrapper {

    private final ByteArrayOutputStream content = new ByteArrayOutputStream();
    private final ServletOutputStream outputStream = new CachedServletOutputStream(content);
    private PrintWriter writer;
    private Integer status;
    private String contentType;

    public CachingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(outputStream, true, StandardCharsets.UTF_8);
        }
        return writer;
    }

    @Override
    public void setStatus(int sc) {
        super.setStatus(sc);
        this.status = sc;
    }

    @Override
    public void sendError(int sc) throws IOException {
        super.sendError(sc);
        this.status = sc;
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        super.sendError(sc, msg);
        this.status = sc;
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        super.sendRedirect(location);
        this.status = HttpServletResponse.SC_FOUND;
    }

    @Override
    public void setContentType(String type) {
        super.setContentType(type);
        this.contentType = type;
    }

    @Override
    public int getStatus() {
        if (status != null) {
            return status;
        }
        return super.getStatus();
    }

    @Override
    public String getContentType() {
        if (contentType != null) {
            return contentType;
        }
        return super.getContentType();
    }

    public byte[] getContent() {
        return content.toByteArray();
    }

    public String getContentAsString() {
        return new String(getContent(), StandardCharsets.UTF_8);
    }

    public int getContentSize() {
        return content.size();
    }

    public boolean hasContent() {
        return content.size() > 0;
    }

    public void copyBodyToResponse() throws IOException {
        byte[] body = getContent();
        if (body.length > 0) {
            HttpServletResponse response = (HttpServletResponse) getResponse();
            ServletOutputStream originalOutputStream = response.getOutputStream();
            originalOutputStream.write(body);
            originalOutputStream.flush();
        }
    }

    private static class CachedServletOutputStream extends ServletOutputStream {
        private final ByteArrayOutputStream buffer;

        public CachedServletOutputStream(ByteArrayOutputStream buffer) {
            this.buffer = buffer;
        }

        @Override
        public void write(int b) throws IOException {
            buffer.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            buffer.write(b, off, len);
        }

        @Override
        public void write(byte[] b) throws IOException {
            buffer.write(b);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // Not implemented for synchronous writing
            throw new UnsupportedOperationException("Not supported");
        }
    }
}