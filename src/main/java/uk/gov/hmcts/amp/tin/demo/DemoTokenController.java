package uk.gov.hmcts.amp.tin.demo;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.tin.auth.AuthProperties;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The demo authorisation server, for the self-issued realm only.
 *
 * <p>Absent from the application under ENTRA - see {@link DemoKeys}. A token minted here is worth
 * nothing anywhere else: it carries this engine's own issuer and audience, so it cannot be replayed
 * against a real HMCTS API even by accident.
 */
@RestController
@ConditionalOnProperty(name = "auth.key-source", havingValue = "SELF_ISSUED", matchIfMissing = true)
public class DemoTokenController {

    private static final long LIFETIME_SECONDS = 3600;

    private final DemoKeys keys;
    private final ApiRegistry registry;
    private final AuthProperties auth;

    public DemoTokenController(final DemoKeys keys,
                               final ApiRegistry registry,
                               final AuthProperties auth) {
        this.keys = keys;
        this.registry = registry;
        this.auth = auth;
    }

    @GetMapping(value = "/.well-known/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return keys.jwks();
    }

    /**
     * Mints a demo token for one API.
     *
     * <p>{@code api} is required and is what the token is scoped to - exactly as a real
     * client_credentials request names one resource. A token minted for slc carries slc's role and
     * is refused on an rcc path, in this realm as in the Entra one.
     */
    @PostMapping(value = "/oauth2/v2.0/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "api") final String api,
            @RequestParam(name = "grant_type", defaultValue = "client_credentials") final String grantType,
            @RequestParam(name = "entitled", defaultValue = "true") final boolean entitled) {

        final Optional<ApiDefinition> definition = registry.byShortCode(api);
        if (definition.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "invalid_request",
                    "error_description", "No API is registered as '" + api + "'."));
        }
        if (!"client_credentials".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "unsupported_grant_type",
                    "error_description", "Only client_credentials is supported."));
        }

        try {
            final Instant now = Instant.now();
            final JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject("demo-client-" + api)
                    // Scoped to the one API asked for, exactly as a real
                    // client_credentials request names one resource.
                    .audience(auth.audienceFor(definition.get()))
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(LIFETIME_SECONDS)));
            // "entitled=false" mints a token with no roles - the shape a real client gets when it
            // holds no grant, and the only way to rehearse a 403 without a second registration.
            if (entitled) {
                claims.claim("roles", List.of(definition.get().role()));
            }

            final SignedJWT jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(keys.getSigningKey().getKeyID())
                            .type(JOSEObjectType.JWT).build(),
                    claims.build());
            jwt.sign(new RSASSASigner(keys.getSigningKey()));

            return ResponseEntity.ok(Map.of(
                    "access_token", jwt.serialize(),
                    "token_type", "Bearer",
                    "expires_in", LIFETIME_SECONDS,
                    "scope", api));
        } catch (final Exception failed) {
            return ResponseEntity.status(500).body(Map.of("error", "server_error"));
        }
    }
}
