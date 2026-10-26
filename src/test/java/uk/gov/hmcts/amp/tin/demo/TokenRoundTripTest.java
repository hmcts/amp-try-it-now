package uk.gov.hmcts.amp.tin.demo;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import uk.gov.hmcts.amp.tin.auth.AuthProperties;
import uk.gov.hmcts.amp.tin.auth.TokenValidationException;
import uk.gov.hmcts.amp.tin.auth.TokenValidator;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.registry.TestRegistry;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The self-issued realm end to end: mint a token at the demo endpoint, then present it to the
 * validator that guards a replay.
 *
 * <p>Deliberately wires the REAL registry, keys, controller and validator rather than mocking
 * between them. The things worth protecting here are properties of the seam - that the audience
 * the minter writes is the audience the validator demands, and that a token for one API is refused
 * on another - and a mock at that seam asserts only that the mock was configured.
 */
class TokenRoundTripTest {

    private DemoKeys keys;
    private ApiRegistry registry;
    private DemoTokenController controller;
    private TokenValidator validator;
    private AuthProperties auth;

    @BeforeEach
    void setUp() {
        keys = new DemoKeys("");
        auth = new AuthProperties("SELF_ISSUED", "", "", "", "tin", 60);
        registry = TestRegistry.selfIssued();
        controller = new DemoTokenController(keys, registry, auth);

        @SuppressWarnings("unchecked")
        final ObjectProvider<DemoKeys> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(keys);
        validator = new TokenValidator(auth, provider);
    }

    private String mint(final String api, final boolean entitled) {
        final ResponseEntity<Map<String, Object>> response =
                controller.token(api, "client_credentials", entitled);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return String.valueOf(response.getBody().get("access_token"));
    }

    private ApiDefinition api(final String shortCode) {
        return registry.byShortCode(shortCode).orElseThrow();
    }

    @Test
    @DisplayName("a minted token validates against the API it was minted for")
    void roundTrip() {
        final TokenValidator.ValidatedCaller caller = validator.validate(mint("slc", true), api("slc"));

        assertThat(caller.apiId()).isEqualTo("slc");
        assertThat(caller.clientId()).isEqualTo("demo-client-slc");
    }

    @Test
    @DisplayName("an slc token is refused on rcc, on the audience, before any role is read")
    void audienceBoundaryHolds() {
        // The assertion the whole consolidation rests on. Both APIs carry the same app.read role,
        // so if this ever passes it means the audience stopped being per-API and the role - which
        // cannot tell them apart - became the only check left.
        assertThatThrownBy(() -> validator.validate(mint("slc", true), api("rcc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(TokenValidationException.Reason.WRONG_AUDIENCE);
    }

    @Test
    @DisplayName("entitled=false mints a usable token with no roles, which is a 403 not a 401")
    void unentitledTokenIsForbiddenNotUnauthorised() {
        // Authenticated and unentitled must stay distinguishable from unauthenticated: the former
        // means "your registration lacks the grant", the latter "your token is not valid".
        assertThatThrownBy(() -> validator.validate(mint("slc", false), api("slc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .satisfies(reason -> {
                    assertThat(reason).isEqualTo(TokenValidationException.Reason.MISSING_ROLE);
                    assertThat(((TokenValidationException.Reason) reason).isAuthenticationFailure()).isFalse();
                    assertThat(((TokenValidationException.Reason) reason).getErrorCode())
                            .isEqualTo("insufficient_scope");
                });
    }

    @Test
    @DisplayName("no token at all is MISSING_TOKEN, not a signature failure")
    void missingToken() {
        assertThatThrownBy(() -> validator.validate(null, api("slc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(TokenValidationException.Reason.MISSING_TOKEN);
        assertThatThrownBy(() -> validator.validate("   ", api("slc")))
                .isInstanceOf(TokenValidationException.class);
    }

    @Test
    @DisplayName("text that is not a JWT is MALFORMED_TOKEN")
    void malformedToken() {
        assertThatThrownBy(() -> validator.validate("not-a-jwt", api("slc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(TokenValidationException.Reason.MALFORMED_TOKEN);
    }

    @Test
    @DisplayName("a token signed with someone else's key does not verify")
    void forgedSignature() throws Exception {
        // The assertion that the self-issued realm actually verifies rather than merely parses.
        // It did only parse once, which meant any well-formed JWT was accepted.
        final DemoKeys attacker = new DemoKeys("");
        final SignedJWT forged = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(keys.getSigningKey().getKeyID()).type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder()
                        .subject("attacker")
                        .audience(auth.audienceFor(api("slc")))
                        .claim("roles", List.of("app.read"))
                        .expirationTime(Date.from(Instant.now().plusSeconds(3600)))
                        .build());
        forged.sign(new RSASSASigner(attacker.getSigningKey()));

        assertThatThrownBy(() -> validator.validate(forged.serialize(), api("slc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(TokenValidationException.Reason.UNTRUSTED_SIGNATURE);
    }

    @Test
    @DisplayName("an expired token is refused even though it verifies")
    void expiredToken() throws Exception {
        final SignedJWT stale = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(keys.getSigningKey().getKeyID()).type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder()
                        .subject("demo-client-slc")
                        .audience(auth.audienceFor(api("slc")))
                        .claim("roles", List.of("app.read"))
                        .expirationTime(Date.from(Instant.now().minusSeconds(7200)))
                        .build());
        stale.sign(new RSASSASigner(keys.getSigningKey()));

        assertThatThrownBy(() -> validator.validate(stale.serialize(), api("slc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(TokenValidationException.Reason.EXPIRED);
    }

    @Test
    @DisplayName("a token with no exp is refused rather than treated as eternal")
    void tokenWithoutExpiry() throws Exception {
        final SignedJWT eternal = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .keyID(keys.getSigningKey().getKeyID()).type(JOSEObjectType.JWT).build(),
                new JWTClaimsSet.Builder().subject("x")
                        .audience(auth.audienceFor(api("slc")))
                        .claim("roles", List.of("app.read")).build());
        eternal.sign(new RSASSASigner(keys.getSigningKey()));

        assertThatThrownBy(() -> validator.validate(eternal.serialize(), api("slc")))
                .isInstanceOf(TokenValidationException.class)
                .extracting(e -> ((TokenValidationException) e).getReason())
                .isEqualTo(TokenValidationException.Reason.EXPIRED);
    }

    @Test
    @DisplayName("the token endpoint refuses an unregistered API with 404, not a token")
    void unregisteredApi() {
        final ResponseEntity<Map<String, Object>> response =
                controller.token("nope", "client_credentials", true);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("error", "invalid_request");
    }

    @Test
    @DisplayName("only client_credentials is supported")
    void unsupportedGrant() {
        final ResponseEntity<Map<String, Object>> response =
                controller.token("slc", "password", true);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("error", "unsupported_grant_type");
    }

    @Test
    @DisplayName("the token response carries no secret, only the token")
    void responseCarriesNoCredentials() {
        final ResponseEntity<Map<String, Object>> response =
                controller.token("slc", "client_credentials", true);

        assertThat(response.getBody().keySet())
                .containsExactlyInAnyOrder("access_token", "token_type", "expires_in", "scope");
    }

    @Test
    @DisplayName("JWKS publishes the public half and never the private one")
    void jwksIsPublicOnly() {
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> published =
                (List<Map<String, Object>>) controller.jwks().get("keys");

        assertThat(published).hasSize(1);
        assertThat(published.getFirst())
                .containsEntry("kty", "RSA")
                .containsKeys("n", "e", "kid");
        // d, p and q are the private exponent and its prime factors. Any of them in a published
        // JWKS hands over the signing key, which is why this asserts their ABSENCE rather than
        // asserting the presence of the public ones alone - the latter passes either way.
        assertThat(published.getFirst()).doesNotContainKeys("d", "p", "q", "dp", "dq", "qi");
    }
}
