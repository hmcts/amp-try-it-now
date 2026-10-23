package uk.gov.hmcts.amp.tin.registry;

import java.util.List;

/**
 * One API, as the registry declares it.
 *
 * @param shortCode the path prefix this API answers on, and the key everything else hangs off
 * @param role      the application role a caller's token must carry for this API
 * @param audience  the Entra resource app id tokens must name, resolved from {@code audienceEnv}.
 *                  Blank in the self-issued realm, where the engine is its own audience.
 */
public record ApiDefinition(String shortCode,
                            String title,
                            String catalogueName,
                            String productId,
                            String role,
                            String audience,
                            List<Operation> operations) {

    /**
     * One operation an API exposes, as its OpenAPI spec declares it.
     *
     * @param path the template as the spec writes it, e.g. {@code /courthouses/{court_id}}
     */
    public record Operation(String id, String method, String path) {
    }
}
