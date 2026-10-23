package uk.gov.hmcts.amp.tin.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.tin.auth.AuthProperties;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.scenarios.Scenario;
import uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The entry point for one API, and the only endpoint a consumer needs to know.
 *
 * <p>Unauthenticated and self-documenting: which requests are recorded, what each demonstrates, and
 * which realm this deployment is in. Ask the deployment rather than trusting a configuration file.
 *
 * <p>It returns no credentials, in either realm.
 */
@RestController
public class ScenariosController {

    private final ScenarioCatalog catalogue;
    private final ApiRegistry registry;
    private final AuthProperties auth;

    public ScenariosController(final ScenarioCatalog catalogue,
                               final ApiRegistry registry,
                               final AuthProperties auth) {
        this.catalogue = catalogue;
        this.registry = registry;
        this.auth = auth;
    }

    @GetMapping("/{shortCode}/scenarios")
    public ResponseEntity<Map<String, Object>> scenarios(@PathVariable final String shortCode) {
        final Optional<ApiDefinition> api = registry.byShortCode(shortCode);
        if (api.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "not_found",
                    "message", "No API is registered at this path."));
        }

        final ApiDefinition definition = api.get();
        final List<Map<String, Object>> scenarios = catalogue.all(shortCode).stream()
                .map(ScenariosController::describe)
                .toList();

        return ResponseEntity.ok(Map.of(
                "api", definition.shortCode(),
                "title", definition.title(),
                "description", "Sandbox for " + definition.title()
                        + ". Responses are recorded examples - no Common Platform data is reachable "
                        + "from here, and no real case data is present.",
                "authentication", Map.of(
                        "mode", auth.isEntraRealm() ? "entra" : "self-issued",
                        "requiredRole", definition.role()),
                "scenarios", scenarios));
    }

    private static Map<String, Object> describe(final Scenario scenario) {
        return Map.of(
                "request", scenario.method() + " /" + scenario.apiId() + "/" + scenario.requestKey(),
                "operation", scenario.operationId(),
                "status", scenario.status(),
                "summary", scenario.summary() == null ? "" : scenario.summary(),
                "recordedFrom", scenario.recordedFrom());
    }
}
