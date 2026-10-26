package uk.gov.hmcts.amp.tin.filters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import uk.gov.hmcts.amp.tin.auth.AuthorizationPolicy;
import uk.gov.hmcts.amp.tin.auth.TokenValidationException;
import uk.gov.hmcts.amp.tin.auth.TokenValidationException.Reason;
import uk.gov.hmcts.amp.tin.auth.TokenValidator;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.registry.TestRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The filter is where a refusal gets its status code, and the three refusals mean different things
 * to a consumer: 401 "your token is not valid", 403 "your token is valid and holds no grant",
 * 404 "there is no such API". Collapsing any pair of them sends someone hunting in the wrong place.
 */
class ApiAuthFilterTest {

    private TokenValidator validator;
    private ApiAuthFilter filter;
    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        validator = mock(TokenValidator.class);
        final ApiRegistry registry = TestRegistry.selfIssued();
        filter = new ApiAuthFilter(validator, new AuthorizationPolicy(), registry);
        response = new MockHttpServletResponse();
        chain = new MockFilterChain();
    }

    private MockHttpServletRequest get(final String uri, final String authorization) {
        final MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    private void refusedBecause(final Reason reason) {
        when(validator.validate(any(), any())).thenThrow(new TokenValidationException(reason, "detail"));
    }

    @Test
    @DisplayName("a valid token passes through to the controller")
    void validTokenPassesThrough() throws Exception {
        when(validator.validate(eq("good-token"), any()))
                .thenReturn(new TokenValidator.ValidatedCaller("client-1", "slc"));

        filter.doFilter(get("/slc/case/ABC/courtschedule", "Bearer good-token"), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("/{shortCode}/scenarios is exempt, so discovery needs no token")
    void scenariosIsExempt() throws Exception {
        filter.doFilter(get("/slc/scenarios", null), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        // A consumer reads /scenarios to find out which realm it is in. Requiring a token here
        // would mean needing a token to discover how to get one.
        verify(validator, never()).validate(any(), any());
    }

    @Test
    @DisplayName("health is exempt, including its sub-paths, or the probes get 401 and the pod never goes ready")
    void healthIsExempt() throws Exception {
        filter.doFilter(get("/health/readiness", null), response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(validator, never()).validate(any(), any());
    }

    @Test
    @DisplayName("no token is 401 invalid_request, with a WWW-Authenticate header")
    void missingTokenIs401() throws Exception {
        refusedBecause(Reason.MISSING_TOKEN);

        filter.doFilter(get("/slc/case/ABC/courtschedule", null), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("invalid_request");
        assertThat(response.getContentAsString()).contains("invalid_request");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("the wrong audience is 401, not 403 - the token is not valid HERE")
    void wrongAudienceIs401() throws Exception {
        refusedBecause(Reason.WRONG_AUDIENCE);

        filter.doFilter(get("/rcc/courthouses/X", "Bearer slc-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("invalid_token");
    }

    @Test
    @DisplayName("a valid token with no role is 403, not 401")
    void missingRoleIs403() throws Exception {
        refusedBecause(Reason.MISSING_ROLE);

        filter.doFilter(get("/slc/case/ABC/courtschedule", "Bearer no-roles"), response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("insufficient_scope");
    }

    @Test
    @DisplayName("an unregistered short code is 404 and carries no WWW-Authenticate")
    void unknownApiIs404() throws Exception {
        filter.doFilter(get("/nope/anything", "Bearer whatever"), response, chain);

        // 404 rather than 401: the registry is public - it is published in the catalogue - so
        // withholding it buys nothing, and a 401 would send a caller hunting through their token
        // for a problem that is a typo in the path.
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("not_found");
        assertThat(response.getHeader("WWW-Authenticate")).isNull();
    }

    @Test
    @DisplayName("a refusal body never echoes the token or a claim")
    void refusalBodyIsCoarse() throws Exception {
        refusedBecause(Reason.UNTRUSTED_SIGNATURE);

        filter.doFilter(get("/slc/case/ABC/courtschedule", "Bearer super-secret-token"), response, chain);

        assertThat(response.getContentAsString())
                .doesNotContain("super-secret-token")
                .doesNotContain("TokenValidationException");
    }

    @Test
    @DisplayName("a header that is not Bearer is treated as no token at all")
    void nonBearerHeaderIsNoToken() throws Exception {
        when(validator.validate(eq(null), any()))
                .thenThrow(new TokenValidationException(Reason.MISSING_TOKEN, "no bearer"));

        filter.doFilter(get("/slc/case/ABC/courtschedule", "Basic dXNlcjpwYXNz"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
