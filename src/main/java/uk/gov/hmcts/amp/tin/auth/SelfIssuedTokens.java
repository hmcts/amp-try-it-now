package uk.gov.hmcts.amp.tin.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import uk.gov.hmcts.amp.tin.auth.TokenValidationException.Reason;
import uk.gov.hmcts.amp.tin.demo.DemoKeys;

import java.time.Instant;

/**
 * The self-issued realm: local, preview, AAT staging, AAT and demo.
 *
 * <p>Tokens are verified <b>for real</b> - the signature is checked against this engine's own key
 * and the claims are checked exactly as the Entra realm checks them. The only difference between
 * the realms is who signed the token. There is no mode in which validation is skipped.
 */
final class SelfIssuedTokens {

    private SelfIssuedTokens() {
    }

    /* default */ static JWTClaimsSet verify(final String bearer,
                               final AuthProperties properties,
                               final DemoKeys keys) {
        final SignedJWT jwt;
        try {
            jwt = SignedJWT.parse(bearer);
        } catch (final Exception malformed) {
            throw new TokenValidationException(Reason.MALFORMED_TOKEN, "not a signed JWT");
        }

        // The signature, first. Without this the realm would accept anything shaped like a token,
        // which is precisely the "auth is mocked locally" failure this design exists to avoid.
        try {
            if (keys == null || !jwt.verify(new RSASSAVerifier(keys.getSigningKey().toPublicJWK()))) {
                throw new TokenValidationException(Reason.UNTRUSTED_SIGNATURE,
                        "signature does not verify against the demo key");
            }
        } catch (final TokenValidationException rethrow) {
            throw rethrow;
        } catch (final Exception unverifiable) {
            throw new TokenValidationException(Reason.UNTRUSTED_SIGNATURE, "signature unverifiable");
        }

        final JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (final Exception unreadable) {
            throw new TokenValidationException(Reason.MALFORMED_TOKEN, "claims unreadable");
        }

        final Instant expiry = claims.getExpirationTime() == null
                ? null : claims.getExpirationTime().toInstant();
        if (expiry == null
                || expiry.isBefore(Instant.now().minusSeconds(properties.getClockSkewSeconds()))) {
            throw new TokenValidationException(Reason.EXPIRED, "expired or no exp");
        }
        return claims;
    }
}
