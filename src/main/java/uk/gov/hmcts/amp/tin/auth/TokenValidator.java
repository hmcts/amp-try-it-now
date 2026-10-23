package uk.gov.hmcts.amp.tin.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.amp.tin.auth.TokenValidationException.Reason;
import org.springframework.beans.factory.ObjectProvider;
import uk.gov.hmcts.amp.tin.demo.DemoKeys;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Verifies a bearer token against the realm this environment runs in.
 *
 * <p>The same code runs in both realms. What differs is where the signing keys come from - and,
 * critically, the audience, which is resolved <b>per API</b> from the registry. A token minted for
 * one API is refused on another's path before any role is read, which is what makes the audience the
 * access boundary rather than a label.
 */
@Slf4j
@Component
public class TokenValidator {

    private final AuthProperties properties;
    private final DefaultJWTProcessor<SecurityContext> processor;
    /** Absent under ENTRA - the demo key does not exist in that realm. */
    private final ObjectProvider<DemoKeys> demoKeys;

    public TokenValidator(final AuthProperties properties, final ObjectProvider<DemoKeys> demoKeys) {
        this.properties = properties;
        this.demoKeys = demoKeys;
        this.processor = properties.isEntraRealm() ? entraProcessor(properties) : null;
    }

    private static DefaultJWTProcessor<SecurityContext> entraProcessor(final AuthProperties props) {
        try {
            final JWKSource<SecurityContext> keys =
                    JWKSourceBuilder.create(URI.create(props.getJwksUri()).toURL()).build();
            final DefaultJWTProcessor<SecurityContext> jwt = new DefaultJWTProcessor<>();
            jwt.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, keys));
            // Audience is checked per API below, not here: one processor serves every API.
            jwt.setJWTClaimsSetVerifier(new DefaultJWTClaimsVerifier<>(
                    null, new JWTClaimsSet.Builder().issuer(props.getIssuer()).build(),
                    Set.of("sub", "iat", "exp", "aud")));
            return jwt;
        } catch (final Exception unusable) {
            throw new IllegalStateException("Could not build the Entra token processor", unusable);
        }
    }

    /**
     * Verifies a bearer token and holds it against one API's audience and role.
     *
     * @param api the API the request is addressed to. Its audience and role are what the token is
     *            held against - not a service-wide pair.
     */
    public ValidatedCaller validate(final String bearer, final ApiDefinition api) {
        if (bearer == null || bearer.isBlank()) {
            throw new TokenValidationException(Reason.MISSING_TOKEN, "no Authorization header");
        }
        final JWTClaimsSet claims = properties.isEntraRealm()
                ? verifyEntra(bearer)
                : SelfIssuedTokens.verify(bearer, properties, demoKeys.getIfAvailable());

        requireAudience(claims, api);
        requireRole(claims, api);
        return new ValidatedCaller(claims.getSubject(), api.shortCode());
    }

    private JWTClaimsSet verifyEntra(final String bearer) {
        try {
            return processor.process(bearer, null);
        } catch (final Exception refused) {
            throw new TokenValidationException(Reason.UNTRUSTED_SIGNATURE, refused.getMessage());
        }
    }

    /**
     * The audience check, and the first thing read after the signature.
     *
     * <p>A correct token for the wrong API fails here - no role lookup, no database read, and no
     * dependence on the other API having configured anything correctly.
     */
    private void requireAudience(final JWTClaimsSet claims, final ApiDefinition api) {
        final String expected = properties.audienceFor(api);
        final List<String> actual = claims.getAudience();
        if (expected == null || expected.isBlank()) {
            throw new TokenValidationException(Reason.WRONG_AUDIENCE,
                    "no audience configured for " + api.shortCode());
        }
        if (actual == null || !new HashSet<>(actual).contains(expected)) {
            throw new TokenValidationException(Reason.WRONG_AUDIENCE,
                    "token audience does not match " + api.shortCode());
        }
    }

    private void requireRole(final JWTClaimsSet claims, final ApiDefinition api) {
        final Object roles = claims.getClaim("roles");
        final boolean held = roles instanceof List<?> list
                && list.stream().anyMatch(role -> api.role().equals(String.valueOf(role)));
        if (!held) {
            // A client with no grant still receives a valid token - it simply has no roles claim.
            // That is a 403, and saying so plainly saves a long hunt through the token request.
            throw new TokenValidationException(Reason.MISSING_ROLE,
                    "token carries no " + api.role() + " for " + api.shortCode());
        }
    }

    /**
     * A caller whose token verified against the API it addressed.
     *
     * @param clientId the token subject, carried into the MDC for correlation
     */
    public record ValidatedCaller(String clientId, String apiId) {
    }
}
