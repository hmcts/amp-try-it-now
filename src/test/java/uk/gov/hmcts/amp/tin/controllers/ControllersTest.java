package uk.gov.hmcts.amp.tin.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import uk.gov.hmcts.amp.tin.auth.AuthProperties;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.registry.TestRegistry;
import uk.gov.hmcts.amp.tin.scenarios.Scenario;
import uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The two endpoints a consumer actually calls: discovery, and the replay it points at. */
class ControllersTest {

    private static final Scenario SLC_OK = new Scenario(
            "allocated", "slc", "getCourtScheduleByCaseUrn", "GET",
            "case/tin-allocated-01/courtschedule", 200, "{\"courtSchedule\":[]}",
            Map.of("X-Recorded", "fixture"), "An allocated case", "fixture");

    private ScenarioCatalog catalogue;
    private ApiRegistry registry;
    private ScenariosController scenarios;
    private ReplayController replay;

    @BeforeEach
    void setUp() {
        catalogue = mock(ScenarioCatalog.class);
        registry = TestRegistry.selfIssued();
        scenarios = new ScenariosController(catalogue, registry,
                new AuthProperties("SELF_ISSUED", "", "", "", "tin", 60));
        replay = new ReplayController(catalogue, registry);
    }

    private MockHttpServletRequest request(final String method, final String uri, final String query) {
        final MockHttpServletRequest http = new MockHttpServletRequest(method, uri);
        http.setRequestURI(uri);
        http.setQueryString(query);
        return http;
    }

    @Test
    @DisplayName("scenarios describes the API, its realm and what it serves")
    void scenariosDescribesTheApi() {
        when(catalogue.all("slc")).thenReturn(List.of(SLC_OK));

        final Map<String, Object> body = scenarios.scenarios("slc").getBody();

        assertThat(body).containsEntry("api", "slc");

        @SuppressWarnings("unchecked")
        final Map<String, Object> authentication = (Map<String, Object>) body.get("authentication");
        assertThat(authentication)
                .containsEntry("mode", "self-issued")
                .containsEntry("requiredRole", "app.read");
        assertThat((List<?>) body.get("scenarios")).hasSize(1);
    }

    @Test
    @DisplayName("scenarios returns no credentials, in either realm")
    void scenariosLeaksNoCredentials() {
        when(catalogue.all("slc")).thenReturn(List.of(SLC_OK));

        // Read by consumers evaluating the live API, most of whom hold their own Entra
        // registration. A client id or secret here would invite them to use the wrong one.
        final String rendered = String.valueOf(scenarios.scenarios("slc").getBody()).toLowerCase();

        assertThat(rendered).doesNotContain("client_secret", "clientsecret", "password");
    }

    @Test
    @DisplayName("an unregistered API is 404, not an empty catalogue")
    void scenariosUnknownApi() {
        final ResponseEntity<Map<String, Object>> response = scenarios.scenarios("nope");

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("error", "not_found");
    }

    @Test
    @DisplayName("replay serves the recorded body, status and headers verbatim")
    void replayServesTheRecording() {
        when(catalogue.find(eq("slc"), eq("GET"), any())).thenReturn(Optional.of(SLC_OK));

        final ResponseEntity<String> response =
                replay.replay("slc", request("GET", "/slc/case/TIN-ALLOCATED-01/courtschedule", null));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo("{\"courtSchedule\":[]}");
        assertThat(response.getHeaders().getFirst("X-Recorded")).isEqualTo("fixture");
    }

    @Test
    @DisplayName("a recorded non-200 is replayed as itself, not as an error")
    void replayServesRecordedFailures() {
        final Scenario recorded404 = new Scenario("missing", "slc", "getCourtScheduleByCaseUrn",
                "GET", "case/nope/courtschedule", 404, "{\"error\":\"CASE_NOT_FOUND\"}",
                Map.of(), "The API's own 404", "fixture");
        when(catalogue.find(eq("slc"), eq("GET"), any())).thenReturn(Optional.of(recorded404));

        final ResponseEntity<String> response =
                replay.replay("slc", request("GET", "/slc/case/NOPE/courtschedule", null));

        // The recorded 404 is the demonstrated API's own, and must not be confused with the
        // engine's "nothing recorded" 404 - which is why the body matters, not just the status.
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("CASE_NOT_FOUND").doesNotContain("not_found");
    }

    @Test
    @DisplayName("nothing recorded is the engine's own 404, which points at /scenarios")
    void replayNothingRecorded() {
        when(catalogue.find(any(), any(), any())).thenReturn(Optional.empty());

        final ResponseEntity<String> response =
                replay.replay("slc", request("GET", "/slc/case/UNKNOWN/courtschedule", null));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("not_found").contains("/slc/scenarios");
    }

    @Test
    @DisplayName("an unregistered short code never reaches the catalogue")
    void replayUnregisteredApi() {
        final ResponseEntity<String> response =
                replay.replay("nope", request("GET", "/nope/anything", null));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("No API is registered at this path.");
    }

    @Test
    @DisplayName("the query string is part of the lookup key")
    void replayUsesTheQueryString() {
        when(catalogue.find(eq("slc"), eq("GET"), eq("case/abc/courtschedule?page=2")))
                .thenReturn(Optional.of(SLC_OK));

        final ResponseEntity<String> response =
                replay.replay("slc", request("GET", "/slc/case/ABC/courtschedule", "page=2"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
