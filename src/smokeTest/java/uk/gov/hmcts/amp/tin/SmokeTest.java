package uk.gov.hmcts.amp.tin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Is the deployed engine alive?
 *
 * <p>The CNP pipeline runs this against a real deployment, with {@code TEST_URL} set by the
 * {@code Smoke Test - AKS <env>} stage. Without at least one test here the stage produces no JUnit
 * report and the build fails on <em>"No test report files were found"</em> having deployed
 * perfectly well - which is how this file came to exist.
 *
 * <p>Kept to liveness, following try-slc. A smoke failure uninstalls the helm release and blocks
 * the build, so anything asserting behaviour belongs in the functional suite.
 */
class SmokeTest {

    private static final String DEFAULT_URL = "http://localhost:8080";
    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final String testUrl = System.getenv().getOrDefault("TEST_URL", DEFAULT_URL);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Test
    @DisplayName("the deployed engine answers /health with UP, and does so without a token")
    void healthIsUpAndUnauthenticated() throws IOException, InterruptedException {
        final HttpResponse<String> response = get("/health");

        assertThat(response.statusCode())
                .describedAs("GET %s/health - a 401 here means the actuator paths are no longer "
                        + "exempt in ApiAuthFilter", testUrl)
                .isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("the liveness probe path chart-java actually uses is reachable")
    void livenessProbePathIsReachable() throws IOException, InterruptedException {
        // chart-java probes /health/liveness, not /health. Exempting only the latter leaves this
        // answering 401, the pod never goes ready, and the deploy burns its full 1250s --wait
        // before helm gives up - which reads as a hang rather than an auth problem.
        assertThat(get("/health/liveness").statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("/scenarios serves without a token, so the engine's own surface is up")
    void scenariosIsServedWithoutAToken() throws IOException, InterruptedException {
        // The one engine path that is exempt by design - discovery has to work before a consumer
        // holds anything. Reachability only, no assertion on content: it proves the filter
        // exemption and that the catalogue loaded from the database, without coupling the smoke
        // test to which fixtures happen to be seeded.
        assertThat(get("/scenarios").statusCode())
                .describedAs("GET %s/scenarios - 401 means the exemption regressed, 500 means the "
                        + "catalogue could not load", testUrl)
                .isEqualTo(200);
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
