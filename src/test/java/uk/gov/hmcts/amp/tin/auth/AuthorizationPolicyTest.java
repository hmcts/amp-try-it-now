package uk.gov.hmcts.amp.tin.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationPolicyTest {

    private final AuthorizationPolicy policy = new AuthorizationPolicy();

    @Test
    @DisplayName("health and its groups are exempt, so the probes never see a 401")
    void healthGroups() {
        assertThat(policy.isExemptFromValidation("/health")).isTrue();
        assertThat(policy.isExemptFromValidation("/health/liveness")).isTrue();
        assertThat(policy.isExemptFromValidation("/health/readiness")).isTrue();
    }

    @Test
    @DisplayName("a path that merely starts with /health is NOT exempt")
    void healthPrefixIsNotEnough() {
        assertThat(policy.isExemptFromValidation("/healthz")).isFalse();
        assertThat(policy.isExemptFromValidation("/health-admin")).isFalse();
        assertThat(policy.isExemptFromValidation("/health/../secrets")).isFalse();
    }

    @Test
    @DisplayName("the scenario listing is exempt for any API - a caller needs it before a token")
    void scenarioListing() {
        assertThat(policy.isExemptFromValidation("/slc/scenarios")).isTrue();
        assertThat(policy.isExemptFromValidation("/rcc/scenarios")).isTrue();
    }

    @Test
    @DisplayName("deny by default - an API's actual operations always need a token")
    void denyByDefault() {
        assertThat(policy.isExemptFromValidation("/slc/case/ABC/courtschedule")).isFalse();
        assertThat(policy.isExemptFromValidation("/rcc/courthouses/C1")).isFalse();
        // Adding an operation under a short code must never be exempted by the listing rule.
        assertThat(policy.isExemptFromValidation("/slc/scenarios/secret")).isFalse();
    }
}
