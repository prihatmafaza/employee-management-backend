package org.emb.accessrequests.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.emb.accessrequests.error.ErrorCodes;
import org.emb.accessrequests.error.ErrorResponseWriter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before Spring Security and rejects request bodies that are too large
 * (413) or not JSON (415). The JSON-only rule is part of the CSRF defence
 * described in {@link SecurityConfig}, so it covers every endpoint, including
 * ones that ignore their body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodyGuardFilter extends OncePerRequestFilter {

    private final long maxBodyBytes;
    private final ErrorResponseWriter errorWriter;

    public RequestBodyGuardFilter(AppProperties properties, ErrorResponseWriter errorWriter) {
        this.maxBodyBytes = properties.maxBodyBytes();
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        boolean chunked = "chunked".equalsIgnoreCase(request.getHeader("Transfer-Encoding"));

        if (contentLength > maxBodyBytes) {
            reject(response, HttpStatus.CONTENT_TOO_LARGE);
            return;
        }
        if ((contentLength > 0 || chunked) && !isJson(request.getContentType())) {
            reject(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            return;
        }
        // Without a Content-Length we can only enforce the limit while the body is read.
        chain.doFilter(chunked ? new SizeLimitedRequest(request, maxBodyBytes) : request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status) throws IOException {
        errorWriter.write(response, status.value(), ErrorCodes.codeFor(status), ErrorCodes.messageFor(status));
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_JSON.includes(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    /** Thrown while reading a body that goes over the limit; mapped to 413. */
    public static class BodyTooLargeException extends IOException {
        BodyTooLargeException() {
            super("Request body too large");
        }
    }

    private static final class SizeLimitedRequest extends HttpServletRequestWrapper {

        private final long limit;
        private ServletInputStream stream;

        SizeLimitedRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) {
                ServletInputStream in = super.getInputStream();
                stream = new ServletInputStream() {
                    private long read;

                    @Override
                    public int read() throws IOException {
                        int b = in.read();
                        if (b != -1) {
                            count(1);
                        }
                        return b;
                    }

                    @Override
                    public int read(byte[] buf, int off, int len) throws IOException {
                        int n = in.read(buf, off, len);
                        if (n > 0) {
                            count(n);
                        }
                        return n;
                    }

                    private void count(int n) throws IOException {
                        read += n;
                        if (read > limit) {
                            throw new BodyTooLargeException();
                        }
                    }

                    @Override
                    public boolean isFinished() {
                        return in.isFinished();
                    }

                    @Override
                    public boolean isReady() {
                        return in.isReady();
                    }

                    @Override
                    public void setReadListener(ReadListener listener) {
                        in.setReadListener(listener);
                    }
                };
            }
            return stream;
        }
    }
}
