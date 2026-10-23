package uk.gov.hmcts.amp.tin.auth;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import uk.gov.hmcts.amp.tin.registry.ApiDefinition;

import java.util.Locale;

/**
 * Which realm this environment runs in, and what that realm needs.
 *
 * <p>Fails startup rather than degrading. The per-API audiences live in the registry; what is here
 * is realm-wide.
 */
@Slf4j
@Getter
@Component
public class AuthProperties {

    public static final String SELF_ISSUED = "SELF_ISSUED";
    public static final String ENTRA = "ENTRA";

    private final String keySource;
    private final String tenantId;
    private final String issuer;
    private final String jwksUri;
    /** Self-issued realm only: the engine is its own audience. Unused under ENTRA. */
    private final String audience;
    private final long clockSkewSeconds;

    public AuthProperties(@Value("${auth.key-source:SELF_ISSUED}") final String keySource,
                          @Value("${auth.tenant-id:}") final String tenantId,
                          @Value("${auth.issuer:}") final String issuer,
                          @Value("${auth.jwks-uri:}") final String jwksUri,
                          @Value("${auth.audience:}") final String audience,
                          @Value("${auth.clock-skew-seconds:60}") final long clockSkewSeconds) {
        this.keySource = keySource == null ? SELF_ISSUED : keySource.toUpperCase(Locale.ROOT);
        this.tenantId = tenantId;
        this.issuer = issuer.isBlank() && !tenantId.isBlank() ? defaultIssuer(tenantId) : issuer;
        this.jwksUri = jwksUri.isBlank() && !tenantId.isBlank() ? defaultJwks(tenantId) : jwksUri;
        this.audience = audience;
        this.clockSkewSeconds = clockSkewSeconds;
    }

    @PostConstruct
    /* default */ void check() {
        if (isEntraRealm()) {
            require(tenantId, "auth.tenant-id");
            require(issuer, "auth.issuer");
            require(jwksUri, "auth.jwks-uri");
        } else {
            require(audience, "auth.audience");
        }
        log.info("Auth initialised realm:{} issuer:{} clockSkewSeconds:{}",
                keySource, issuer.isBlank() ? "self" : issuer, clockSkewSeconds);
    }

    private static void require(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for auth.key-source=" + name);
        }
    }

    public boolean isEntraRealm() {
        return ENTRA.equals(keySource);
    }

    /**
     * The audience a token must carry to be accepted for this API, in either realm.
     *
     * <p>Per API in both, and that is the point. Under ENTRA it is the API's own resource app.
     * Under SELF_ISSUED it is derived from the short code, because a single service-wide audience
     * would leave the generic {@code app.read} role as the only thing between one sandbox and
     * another - which is to say nothing at all. Demo runs self-issued and is the public
     * environment, so the realm that is easiest to get a token in is the one that most needs the
     * boundary to be real.
     */
    public String audienceFor(final ApiDefinition api) {
        return isEntraRealm() ? api.audience() : audience + ":" + api.shortCode();
    }

    private static String defaultIssuer(final String tenant) {
        // login.microsoftonline.com, not ciamlogin.com. The same directory answers on both with a
        // different iss, and a CIAM token is then refused in a way that reads like a bad token.
        return "https://login.microsoftonline.com/" + tenant + "/v2.0";
    }

    private static String defaultJwks(final String tenant) {
        return "https://login.microsoftonline.com/" + tenant + "/discovery/v2.0/keys";
    }
}
