package uk.gov.hmcts.amp.tin.filters;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.amp.tin.auth.AuthorizationPolicy;
import uk.gov.hmcts.amp.tin.auth.TokenValidationException;
import uk.gov.hmcts.amp.tin.auth.TokenValidator;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;

import java.io.IOException;
import java.util.Optional;

/**
 * Authenticates a consumer request against the API its path names.
 *
 * <p>The API is taken from the first path segment, and its audience and role come from the registry.
 * There is no service-wide audience: a token for slc presented on an rcc path is refused on the
 * audience, with a 401, before any role is read.
 */
@Slf4j
@Component
public class ApiAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String MDC_CLIENT_ID = "clientId";
    private static final String MDC_API_ID = "apiId";

    private final TokenValidator tokenValidator;
    private final AuthorizationPolicy policy;
    private final ApiRegistry registry;

    public ApiAuthFilter(final TokenValidator tokenValidator,
                         final AuthorizationPolicy policy,
                         final ApiRegistry registry) {
        this.tokenValidator = tokenValidator;
        this.policy = policy;
        this.registry = registry;
    }

    @Override
    protected boolean shouldNotFilter(@Nonnull final HttpServletRequest request) {
        return CorsUtils.isPreFlightRequest(request)
                || policy.isExemptFromValidation(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(@Nonnull final HttpServletRequest request,
                                    @Nonnull final HttpServletResponse response,
                                    @Nonnull final FilterChain chain)
            throws ServletException, IOException {

        final TokenValidator.ValidatedCaller caller;
        try {
            final ApiDefinition api = resolveApi(request);
            caller = tokenValidator.validate(bearer(request), api);
        } catch (final TokenValidationException refused) {
            reject(request, response, refused);
            return;
        }

        MDC.put(MDC_CLIENT_ID, caller.clientId());
        MDC.put(MDC_API_ID, caller.apiId());
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_CLIENT_ID);
            MDC.remove(MDC_API_ID);
        }
    }

    private ApiDefinition resolveApi(final HttpServletRequest request) {
        final String[] segments = request.getRequestURI().split("/");
        final String shortCode = segments.length > 1 ? segments[1] : "";
        return registry.byShortCode(shortCode).orElseThrow(() -> new TokenValidationException(
                TokenValidationException.Reason.UNKNOWN_API, "unregistered path"));
    }

    private static String bearer(final HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()).trim())
                .orElse(null);
    }

    private void reject(final HttpServletRequest request,
                        final HttpServletResponse response,
                        final TokenValidationException refused) throws IOException {

        final TokenValidationException.Reason reason = refused.getReason();
        log.warn("AUTH REJECT: {} {} reason:{}",
                request.getMethod(), request.getRequestURI(), reason);

        // An unregistered path is a 404, not a 401. The registry is public - it is published in
        // the catalogue - so withholding it buys nothing, and a 401 here would send a caller
        // hunting through their token for a problem that is a typo in the path.
        if (reason == TokenValidationException.Reason.UNKNOWN_API) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            response.setStatus(reason.isAuthenticationFailure()
                    ? HttpServletResponse.SC_UNAUTHORIZED
                    : HttpServletResponse.SC_FORBIDDEN);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    "Bearer error=\"" + reason.getErrorCode() + "\"");
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Coarse body only. Never echo the token, a claim value, or an internal class name.
        response.getWriter().write("{\"error\":\"" + reason.getErrorCode()
                + "\",\"message\":\"" + reason.getDescription() + "\"}");
    }
}
