package uk.gov.hmcts.amp.tin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.amp.tin.config.FixtureSeeder.FixtureSeedingService.hash;

/**
 * The hash decides whether a deployment is a no-op or supersedes a row, so what it covers is a
 * behavioural choice rather than an implementation detail.
 */
class FixtureHashTest {

    private static final String BODY = "{\"courtHouseName\":\"Southwark\"}";

    @Test
    @DisplayName("unchanged input hashes the same, so a redeploy writes nothing")
    void stable() {
        assertThat(hash("op", "GET", "courthouses/c1", 200, BODY, "a courthouse"))
                .isEqualTo(hash("op", "GET", "courthouses/c1", 200, BODY, "a courthouse"));
    }

    @Test
    @DisplayName("a changed body moves the hash")
    void bodyMatters() {
        assertThat(hash("op", "GET", "courthouses/c1", 200, BODY, "s"))
                .isNotEqualTo(hash("op", "GET", "courthouses/c1", 200, "{\"x\":1}", "s"));
    }

    @Test
    @DisplayName("a changed summary moves the hash - it is visible on /scenarios")
    void summaryMatters() {
        // Easy to leave out, and then editing a summary appears to do nothing at all.
        assertThat(hash("op", "GET", "courthouses/c1", 200, BODY, "before"))
                .isNotEqualTo(hash("op", "GET", "courthouses/c1", 200, BODY, "after"));
    }

    @Test
    @DisplayName("a changed status moves the hash")
    void statusMatters() {
        assertThat(hash("op", "GET", "courthouses/c1", 200, BODY, "s"))
                .isNotEqualTo(hash("op", "GET", "courthouses/c1", 404, BODY, "s"));
    }

    @Test
    @DisplayName("fields cannot run together - a separator keeps them distinct")
    void noFieldRunOn() {
        // Without a separator "ab"+"c" and "a"+"bc" would hash alike and an edit could vanish.
        assertThat(hash("ab", "GET", "c", 200, BODY, "s"))
                .isNotEqualTo(hash("a", "GET", "bc", 200, BODY, "s"));
    }
}
