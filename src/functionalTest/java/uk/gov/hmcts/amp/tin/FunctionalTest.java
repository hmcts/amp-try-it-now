package uk.gov.hmcts.amp.tin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consumer-facing surface, against a real deployment.
 *
 * <p>The pipeline runs this after the smoke stage with {@code TEST_URL} set by
 * {@code Functional Test - aat}. Unlike the smoke test it may assert behaviour - but a functional
 * failure uninstalls the helm release, so every assertion here is on <em>shape</em>, never on
 * fixture values. Recordings supersede fixtures by design, so pinning a court id would fail the
 * first time someone publishes a recording and cost a deployment with it.
 *
 * <p>Nothing here mints a token. The demo token endpoint needs {@code demo.signing-key-jwk}, which
 * no environment supplies yet, so a token-bearing test would fail for a configuration reason rather
 * than a code one. What is proved here is the half that needs no credentials: discovery is open,
 * unknown APIs are refused, and everything else demands a token.
 */
class FunctionalTest {

    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", DEFAULT_URL);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * The short codes registry/apis.yaml declares. Written out rather than discovered, so that
     * dropping an API from the registry fails this suite instead of quietly shrinking it.
     */
    @ParameterizedTest(name = "/{0}/scenarios")
    @ValueSource(strings = {"rcc"})
    @DisplayName("each registered API publishes its catalogue without a token")
    void scenariosIsOpenForEveryRegisteredApi(final String shortCode)
            throws IOException, InterruptedException {

        final HttpResponse<String> response = get("/" + shortCode + "/scenarios");

        assertThat(response.statusCode())
                .describedAs("GET %s/%s/scenarios - 401 means the exemption in AuthorizationPolicy "
                        + "regressed, 404 means the API left the registry", testUrl, shortCode)
                .isEqualTo(200);

        final JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.path("api").asText()).isEqualTo(shortCode);
        assertThat(body.path("title").asText()).isNotBlank();
        assertThat(body.path("scenarios").isArray()).isTrue();

        // Shape only. Which scenarios exist depends on what has been recorded, and the catalogue
        // iterates a Map.copyOf map whose order the JDK randomises per JVM run.
        assertThat(body.path("authentication").path("mode").asText())
                .isIn("self-issued", "entra");
        assertThat(body.path("authentication").path("requiredRole").asText()).isNotBlank();
    }

    @Test
    @DisplayName("discovery says no Common Platform data is reachable")
    void catalogueCarriesTheSandboxDisclaimer() throws IOException, InterruptedException {
        // The sentence consumers are shown before they trust anything here. Worth pinning: it is
        // the one place the deployment states that nothing behind it is real.
        final JsonNode body = MAPPER.readTree(get("/slc/scenarios").body());
        assertThat(body.path("description").asText())
                .contains("no Common Platform data is reachable");
    }

    @Test
    @DisplayName("an unregistered API is refused, and not confused with an empty catalogue")
    void unregisteredApiIsNotFound() throws IOException, InterruptedException {
        final HttpResponse<String> response = get("/nope/scenarios");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(MAPPER.readTree(response.body()).path("error").asText()).isEqualTo("not_found");
    }

    @Test
    @DisplayName("everything other than discovery demands a token")
    void replayRefusesAnAnonymousCaller() throws IOException, InterruptedException {
        // The deployed proof that ApiAuthFilter is in the chain at all. /scenarios above is exempt
        // by design, so without this the suite would pass with authentication switched off.
        final HttpResponse<String> response = get("/rcc/courthouses/anything");

        assertThat(response.statusCode())
                .describedAs("GET %s/rcc/courthouses/... without a token", testUrl)
                .isEqualTo(401);
        assertThat(response.headers().firstValue("WWW-Authenticate"))
                .describedAs("a 401 must say how to authenticate")
                .isPresent();
        assertThat(response.body())
                .describedAs("a refusal must not echo anything back to the caller")
                .doesNotContain("anything");
    }

    private HttpResponse<String> get(final String path) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(testUrl + path))
                .timeout(TIMEOUT)
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
