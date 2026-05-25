package com.trading.platform.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that assigns a Correlation ID to every incoming request.
 *
 * <p>Priority:
 * <ol>
 *   <li>Uses the {@code X-Correlation-Id} header if the client supplies one.</li>
 *   <li>Otherwise generates a fresh UUID v4.</li>
 * </ol>
 *
 * <p>The ID is:
 * <ul>
 *   <li>Stored in the Servlet request attribute {@value ATTR_CORRELATION_ID} for handlers.</li>
 *   <li>Injected into SLF4J MDC so it appears in every log line.</li>
 *   <li>Echoed in the {@code X-Correlation-Id} response header for client tracing.</li>
 * </ul>
 */
@Component
@Order(1)
public class CorrelationIdFilter implements Filter {

    public static final String HEADER_NAME       = "X-Correlation-Id";
    public static final String MDC_KEY           = "correlationId";
    public static final String ATTR_CORRELATION_ID = "correlationId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String headerValue = httpReq.getHeader(HEADER_NAME);
        UUID correlationId;
        try {
            correlationId = (headerValue != null && !headerValue.isBlank())
                    ? UUID.fromString(headerValue)
                    : UUID.randomUUID();
        } catch (IllegalArgumentException e) {
            correlationId = UUID.randomUUID();
        }

        httpReq.setAttribute(ATTR_CORRELATION_ID, correlationId);
        httpResp.setHeader(HEADER_NAME, correlationId.toString());
        MDC.put(MDC_KEY, correlationId.toString());

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
