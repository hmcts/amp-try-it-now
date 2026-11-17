package uk.gov.hmcts.amp.tin.filters;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.hmcts.amp.tin.filters.TracingFilter.CORRELATION_ID_KEY;

class TracingFilterTest {

    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}";

    private TracingFilter filter;
    private MockHttpServletResponse response;
    private AtomicReference<String> seenInMdc;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new TracingFilter();
        response = new MockHttpServletResponse();
        seenInMdc = new AtomicReference<>();
        chain = (req, res) -> seenInMdc.set(MDC.get(CORRELATION_ID_KEY));
    }

    private static MockHttpServletRequest get(final String uri, final String correlationId) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        if (correlationId != null) {
            request.addHeader(CORRELATION_ID_KEY, correlationId);
        }
        return request;
    }

    @Test
    @DisplayName("a valid inbound ID is reused, logged and echoed")
    void reusesInboundId() throws Exception {
        filter.doFilter(get("/slc/case/ABC/courtschedule", "abc-123"), response, chain);

        assertThat(seenInMdc.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CORRELATION_ID_KEY)).isEqualTo("abc-123");
        assertThat(MDC.get(CORRELATION_ID_KEY)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"x-correlation-id", "X-CORRELATION-ID"})
    @DisplayName("the header name is matched case-insensitively")
    void matchesHeaderNameInAnyCase(final String headerName) throws Exception {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/slc/scenarios");
        request.addHeader(headerName, "abc-123");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CORRELATION_ID_KEY)).isEqualTo("abc-123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @MethodSource("unsafeIds")
    @DisplayName("a missing or unsafe inbound ID is replaced with a generated one")
    void generatesIdWhenMissingOrUnsafe(final String inbound) throws Exception {
        filter.doFilter(get("/rcc/courthouses/1", inbound), response, chain);

        assertThat(response.getHeader(CORRELATION_ID_KEY)).matches(UUID_PATTERN);
        assertThat(seenInMdc.get()).isEqualTo(response.getHeader(CORRELATION_ID_KEY));
    }

    static Stream<String> unsafeIds() {
        return Stream.of("abc\ninfo: forged", "abc def", "a".repeat(65));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/health", "/health/readiness", "/info", "/prometheus"})
    @DisplayName("platform endpoints are not traced")
    void skipsPlatformEndpoints(final String uri) throws Exception {
        filter.doFilter(get(uri, null), response, chain);

        assertThat(response.getHeader(CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("the ID is cleared even when the request fails")
    void clearsMdcOnFailure() {
        final FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(get("/dl/documents", "abc-123"), response, failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get(CORRELATION_ID_KEY)).isNull();
    }
}
