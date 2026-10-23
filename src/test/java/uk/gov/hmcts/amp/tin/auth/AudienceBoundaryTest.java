package uk.gov.hmcts.amp.tin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the property the whole design rests on: a token minted for one API must not be accepted
 * for another.
 *
 * <p>Written after the self-issued realm was found doing exactly that. The realm used one
 * service-wide audience, and because every API shares the generic {@code app.read} role there was
 * nothing left to tell two sandboxes apart. Demo runs self-issued and is the public environment, so
 * the realm that is easiest to get a token in was the one with no boundary.
 */
class AudienceBoundaryTest {

    private static final ApiDefinition SLC = api("slc", "resource-app-slc");
    private static final ApiDefinition RCC = api("rcc", "resource-app-rcc");

    @Test
    @DisplayName("self-issued: two APIs never share an audience")
    void selfIssuedIsPerApi() {
        final AuthProperties props = selfIssued();
        assertThat(props.audienceFor(SLC))
                .isNotEqualTo(props.audienceFor(RCC))
                .contains("slc");
    }

    @Test
    @DisplayName("entra: the audience is the API's own resource app")
    void entraIsPerApi() {
        final AuthProperties props = entra();
        assertThat(props.audienceFor(SLC)).isEqualTo("resource-app-slc");
        assertThat(props.audienceFor(RCC)).isEqualTo("resource-app-rcc");
    }

    @Test
    @DisplayName("the role alone cannot be relied on - both APIs use the same generic one")
    void roleIsNotTheBoundary() {
        // Roles are generic BECAUSE the audience is per API. If that ever inverts, this fails and
        // says why rather than leaving a silently shared boundary.
        assertThat(SLC.role()).isEqualTo(RCC.role());
    }

    private static AuthProperties selfIssued() {
        return new AuthProperties("SELF_ISSUED", "", "", "", "amp-try-it-now-demo", 60);
    }

    private static AuthProperties entra() {
        return new AuthProperties("ENTRA", "tenant", "https://issuer/v2.0",
                "https://issuer/keys", "", 60);
    }

    private static ApiDefinition api(final String shortCode, final String audience) {
        return new ApiDefinition(shortCode, shortCode, "api-cp-" + shortCode, shortCode,
                "app.read", audience, List.of());
    }
}
