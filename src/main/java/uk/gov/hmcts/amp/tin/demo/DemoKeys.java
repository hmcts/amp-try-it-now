package uk.gov.hmcts.amp.tin.demo;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * The signing key for the self-issued realm.
 *
 * <p>Exists only where {@code auth.key-source} is SELF_ISSUED. Under ENTRA this bean, the token
 * endpoint and the JWKS are absent from the running application entirely - not present and refusing,
 * which is the difference between a door that is locked and a door that was never built.
 *
 * <p><b>The key must come from configuration in any persistent environment.</b> A key generated at
 * startup is regenerated on every redeploy, which silently invalidates every token already issued.
 * Generation is a local-development convenience and says so, loudly.
 */
@Slf4j
@Getter
@Component
@ConditionalOnProperty(name = "auth.key-source", havingValue = "SELF_ISSUED", matchIfMissing = true)
public class DemoKeys {

    private final RSAKey signingKey;

    public DemoKeys(@Value("${demo.signing-key-jwk:}") final String configuredJwk) {
        this.signingKey = configuredJwk.isBlank() ? generate() : parse(configuredJwk);
    }

    private static RSAKey parse(final String jwk) {
        try {
            final RSAKey key = RSAKey.parse(jwk);
            log.info("Demo signing key loaded from configuration, kid:{}", key.getKeyID());
            return key;
        } catch (final Exception unusable) {
            throw new IllegalStateException(
                    "demo.signing-key-jwk is set but could not be parsed as an RSA JWK", unusable);
        }
    }

    private static RSAKey generate() {
        try {
            final RSAKey key = new RSAKeyGenerator(2048).keyID("demo-ephemeral").generate();
            log.warn("No demo.signing-key-jwk configured - generated an EPHEMERAL signing key. "
                    + "Every restart invalidates previously issued tokens. This is acceptable for "
                    + "local development and must never be the case in a deployed environment.");
            return key;
        } catch (final Exception impossible) {
            throw new IllegalStateException("Could not generate a demo signing key", impossible);
        }
    }

    /** The public half, for anyone verifying a token this engine signed. */
    public Map<String, Object> jwks() {
        return new JWKSet(signingKey.toPublicJWK()).toJSONObject();
    }
}
