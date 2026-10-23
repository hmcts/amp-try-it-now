package uk.gov.hmcts.amp.tin.scenarios;

import java.util.Map;

/**
 * A published example, ready to serve.
 *
 * <p>Holds the body as the text that was validated and approved, not a parsed model. The guarantee
 * that a response cannot contain a field the contract does not define is made at ingest, by checking
 * the body against that API's published OpenAPI schema - not at serve time by re-serialising through
 * a generated class. Moving it earlier is what lets one engine serve every API without compiling
 * against any of them.
 */
public record Scenario(String scenarioId,
                       String apiId,
                       String operationId,
                       String method,
                       String requestKey,
                       int status,
                       String body,
                       Map<String, String> responseHeaders,
                       String summary,
                       String recordedFrom) {
}
