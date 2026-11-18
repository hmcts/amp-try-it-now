package uk.gov.hmcts.amp.tin.filters;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

// Runs before ApiAuthFilter so refusals carry the ID too.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TracingFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_KEY = "X-Correlation-Id";

    private static final Pattern VALID_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected boolean shouldNotFilter(@Nonnull final HttpServletRequest request) {
        final String path = request.getRequestURI();
        return "/".equals(path)
                || path.startsWith("/health")
                || path.startsWith("/info")
                || path.startsWith("/prometheus");
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request,
                                    @Nonnull final HttpServletResponse response,
                                    @Nonnull final FilterChain filterChain) throws ServletException, IOException {
        try {
            final String correlationId = getCorrelationId(request);
            MDC.put(CORRELATION_ID_KEY, correlationId);
            response.setHeader(CORRELATION_ID_KEY, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_KEY);
        }
    }

    private String getCorrelationId(final HttpServletRequest request) {
        final String existingId = request.getHeader(CORRELATION_ID_KEY);
        return existingId != null && VALID_CORRELATION_ID.matcher(existingId).matches()
                ? existingId
                : UUID.randomUUID().toString();
    }
}
