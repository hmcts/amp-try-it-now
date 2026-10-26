package uk.gov.hmcts.amp.tin.controllers;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.scenarios.RequestKey;
import uk.gov.hmcts.amp.tin.scenarios.Scenario;
import uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog;

import java.util.Map;
import java.util.Optional;

/**
 * Serves a recorded response for any registered API.
 *
 * <p>This is the whole consumer surface, and it names no API. A request resolves to an {@code api_id}
 * from its first path segment, a method, and a canonical request key; the recording found under that
 * key is returned exactly as it was approved - status, body and headers.
 *
 * <p>Nothing here parses the body. The guarantee that a response cannot carry a field the contract
 * does not define is made at ingest, against that API's published OpenAPI schema, which is what frees
 * this engine from compiling against any one API's generated model.
 */
@Slf4j
@RestController
public class ReplayController {

    private final ScenarioCatalog catalogue;
    private final ApiRegistry registry;

    public ReplayController(final ScenarioCatalog catalogue, final ApiRegistry registry) {
        this.catalogue = catalogue;
        this.registry = registry;
    }

    // The verbs are named rather than left open: an unlisted method gets Spring's own 405 before
    // any registry lookup, instead of resolving to a short code and reporting "no scenario
    // recorded" - which would read as a missing recording rather than an unsupported method.
    @RequestMapping(value = "/{shortCode}/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                      RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<String> replay(@PathVariable final String shortCode,
                                         final HttpServletRequest request) {
        if (!registry.isRegistered(shortCode)) {
            return notFound("No API is registered at this path.");
        }

        final String method = request.getMethod();
        final String key = RequestKey.from(shortCode, request.getRequestURI(), request.getQueryString());
        final Optional<Scenario> found = catalogue.find(shortCode, method, key);

        if (found.isEmpty()) {
            log.info("No recording for api:{} {} {}", shortCode, method, key);
            return notFound("No scenario is recorded for this request. "
                    + "GET /" + shortCode + "/scenarios lists the ones that are.");
        }

        final Scenario scenario = found.get();
        final ResponseEntity.BodyBuilder reply = ResponseEntity.status(scenario.status())
                .contentType(MediaType.APPLICATION_JSON);
        // Headers are part of a contract - Location on a 201, ETags - so they are replayed too.
        for (final Map.Entry<String, String> header : scenario.responseHeaders().entrySet()) {
            reply.header(header.getKey(), header.getValue());
        }
        return reply.body(scenario.body());
    }

    private static ResponseEntity<String> notFound(final String message) {
        return ResponseEntity.status(404)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"not_found\",\"message\":\"" + message + "\"}");
    }
}
