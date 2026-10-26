package uk.gov.hmcts.amp.tin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.registry.TestRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the ENTRA realm refuses to start without.
 *
 * <p>Every assertion here is about a deployment that would otherwise come up and serve traffic
 * with a trust boundary wider than intended. A blank audience is the dangerous one: it accepts a
 * token minted for any resource in the tenant, which is indistinguishable from working until
 * someone presents a token from an unrelated app. These paths only run in an environment that
 * sandbox is not, so a test is the only thing that exercises them before aat does.
 */
class EntraRealmConfigTest {

    private static AuthProperties auth(final String realm, final String tenant,
                                      final String issuer, final String jwks, final String audience) {
        return new AuthProperties(realm, tenant, issuer, jwks, audience, 60L);
    }

    @Test
    @DisplayName("the ENTRA realm will not start without a tenant")
    void entraNeedsATenant() {
        assertThatThrownBy(() -> auth("ENTRA", "", "", "", "").check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.tenant-id")
                // The realm, not the property name repeated - this message is read by whoever is
                // staring at a CrashLoopBackOff, and it has to say which realm demanded the value.
                .hasMessageContaining("auth.key-source=ENTRA");
    }

    @Test
    @DisplayName("a tenant alone is enough: issuer and JWKS default off it")
    void tenantDefaultsIssuerAndJwks() {
        final AuthProperties properties = auth("ENTRA", "a-tenant-guid", "", "", "");

        properties.check();

        // login.microsoftonline.com, not ciamlogin.com. The same directory answers on both with a
        // different iss, and a CIAM token is then refused in a way that reads like a bad token.
        assertThat(properties.getIssuer())
                .isEqualTo("https://login.microsoftonline.com/a-tenant-guid/v2.0");
        assertThat(properties.getJwksUri())
                .isEqualTo("https://login.microsoftonline.com/a-tenant-guid/discovery/v2.0/keys");
        assertThat(properties.isEntraRealm()).isTrue();
    }

    @Test
    @DisplayName("an explicit issuer and JWKS are left alone")
    void explicitEndpointsWin() {
        final AuthProperties properties =
                auth("ENTRA", "a-tenant-guid", "https://issuer.example", "https://jwks.example", "");

        properties.check();

        assertThat(properties.getIssuer()).isEqualTo("https://issuer.example");
        assertThat(properties.getJwksUri()).isEqualTo("https://jwks.example");
    }

    @Test
    @DisplayName("the self-issued realm will not start without its own audience")
    void selfIssuedNeedsAnAudience() {
        assertThatThrownBy(() -> auth("SELF_ISSUED", "", "", "", "").check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("auth.audience")
                .hasMessageContaining("auth.key-source=SELF_ISSUED");
    }

    @Test
    @DisplayName("an unset or oddly-cased key-source still resolves to a realm")
    void realmIsNormalised() {
        assertThat(auth(null, "", "", "", "x").getKeySource()).isEqualTo("SELF_ISSUED");
        assertThat(auth("entra", "t", "", "", "").isEntraRealm()).isTrue();
    }

    @Test
    @DisplayName("ENTRA will not start when an API's audience variable is unset")
    void entraRefusesABlankPerApiAudience() {
        // The registry names an env var per API; an unset one would mean no audience check at all,
        // and roles are app.read on every API, so audience is the only boundary between them.
        assertThatThrownBy(() -> TestRegistry.entra(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AUTH_AUDIENCE_SLC")
                .hasMessageContaining("widening trust");
    }

    @Test
    @DisplayName("ENTRA resolves each API's audience from its own variable")
    void entraResolvesPerApiAudiences() {
        // The variable names are written out rather than read back from the registry, so this also
        // pins the contract between registry/apis.yaml and the Helm values that set them. Reading
        // them from the same file they are declared in would assert nothing.
        final MockEnvironment environment = new MockEnvironment()
                .withProperty("AUTH_AUDIENCE_SLC", "api://slc-resource-app")
                .withProperty("AUTH_AUDIENCE_RCC", "api://rcc-resource-app");

        final ApiRegistry registry = TestRegistry.entra(environment);

        assertThat(registry.byShortCode("slc")).get()
                .extracting(ApiDefinition::audience).isEqualTo("api://slc-resource-app");
        assertThat(registry.byShortCode("rcc")).get()
                .extracting(ApiDefinition::audience).isEqualTo("api://rcc-resource-app");
        // Each API carries its own, which is the whole boundary: the role is app.read on both.
        assertThat(registry.all()).extracting(ApiDefinition::role).containsOnly("app.read");
    }

    @Test
    @DisplayName("ENTRA refuses to start when only some APIs have an audience")
    void entraRefusesAPartialConfiguration() {
        // The half-configured case is the one that would otherwise pass review: slc works, and rcc
        // is broken in a way nothing notices until someone calls it.
        final MockEnvironment onlySlc =
                new MockEnvironment().withProperty("AUTH_AUDIENCE_SLC", "api://slc-resource-app");

        assertThatThrownBy(() -> TestRegistry.entra(onlySlc))
                .hasMessageContaining("AUTH_AUDIENCE_RCC");
    }

    @Test
    @DisplayName("an unregistered short code resolves to nothing, in either realm")
    void unregisteredShortCodeIsEmpty() {
        final ApiRegistry registry = TestRegistry.selfIssued();

        assertThat(registry.byShortCode("nope")).isEmpty();
        assertThat(registry.isRegistered("nope")).isFalse();
        assertThat(registry.all()).isNotEmpty().allSatisfy(api ->
                assertThat(registry.byShortCode(api.shortCode())).isPresent());
    }
}
