package uk.gov.hmcts.amp.tin.auth;

import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Decides which requests need a token.
 *
 * <p><b>Deny by default.</b> Every request is validated unless its path is listed as exempt below.
 * Exemptions are enumerated, never inferred from a prefix - a prefix rule silently exempts endpoints
 * added later, which is how an endpoint ships unauthenticated without anyone deciding that it should.
 */
@Service
public class AuthorizationPolicy {

    /**
     * The actuator health endpoint and everything under it.
     *
     * <p>The one place the "enumerate, never infer" rule is relaxed, deliberately. chart-java probes
     * {@code /health/liveness} and {@code /health/readiness}, and Spring publishes one path per health
     * group - so enumerating them means the liveness probe starts answering 401 the day somebody adds
     * a group, and the pod stops becoming ready for a reason nothing explains.
     *
     * <p>Matched as the exact path or a child of it, never a bare {@code startsWith}: {@code /healthz}
     * and {@code /health-admin} are <b>not</b> exempt.
     */
    public static final String HEALTH_PREFIX = "/health";

    /** A Spring health group name: one path segment of plain identifier characters. */
    private static final Pattern HEALTH_GROUP = Pattern.compile("[A-Za-z0-9_-]+");

    /** The scenario listing. A caller cannot present a token until it knows what to call. */
    private static final String SCENARIOS = "scenarios";

    /**
     * Infrastructure endpoints carrying no case data. Listed explicitly because
     * {@code management.endpoints.web.base-path} is {@code /} in this service.
     */
    /** The demo authorisation server. A caller cannot present a token until it has fetched one. */
    public static final String PATH_TOKEN = "/oauth2/v2.0/token";
    public static final String PATH_JWKS = "/.well-known/jwks.json";

    /**
     * Infrastructure and demo-discovery endpoints carrying no case data. Listed explicitly because
     * {@code management.endpoints.web.base-path} is {@code /} in this service.
     *
     * <p>The two demo-auth paths are exempt in both realms, and that is safe: under ENTRA they do
     * not exist at all - the beans behind them are conditional on the self-issued realm - so the
     * exemption applies to a 404. Exempting a path that is absent is harmless; conditionally
     * exempting one that is present would be a rule nobody could reason about.
     */
    private static final Set<String> PUBLIC_EXACT_PATHS =
            Set.of("/", "/info", "/prometheus", PATH_TOKEN, PATH_JWKS);

    public boolean isExemptFromValidation(final String requestUri) {
        if (requestUri == null || requestUri.isBlank()) {
            return false;
        }
        final String path = stripTrailingSlash(requestUri);
        return PUBLIC_EXACT_PATHS.contains(path) || isHealth(path) || isScenarioListing(path);
    }

    /**
     * {@code /{shortCode}/scenarios} and nothing else under a short code.
     *
     * <p>Enumerated per API by shape rather than by prefix: the segment after the short code must be
     * exactly {@code scenarios}, so adding an operation to an API never exempts it by accident.
     */
    private boolean isScenarioListing(final String path) {
        final String[] segments = path.split("/");
        return segments.length == 3
                && !segments[1].isBlank()
                && SCENARIOS.equals(segments[2].toLowerCase(Locale.ROOT));
    }

    private boolean isHealth(final String path) {
        if (HEALTH_PREFIX.equals(path)) {
            return true;
        }
        if (!path.startsWith(HEALTH_PREFIX + "/")) {
            return false;
        }
        final String group = path.substring(HEALTH_PREFIX.length() + 1);
        return HEALTH_GROUP.matcher(group).matches();
    }

    private static String stripTrailingSlash(final String uri) {
        return uri.length() > 1 && uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }
}
