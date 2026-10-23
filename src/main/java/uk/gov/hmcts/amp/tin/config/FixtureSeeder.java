package uk.gov.hmcts.amp.tin.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import uk.gov.hmcts.amp.tin.entity.RecordingEntity;
import uk.gov.hmcts.amp.tin.entity.RecordingStatus;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.repository.RecordingRepository;
import uk.gov.hmcts.amp.tin.scenarios.RequestKey;
import uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Seeds committed fixtures, then builds the catalogue.
 *
 * <p>Fixtures and the admin API are two mechanisms with two jobs. Preview and the AAT staging slot
 * are destroyed on every run, so they can only ever start from committed seeds; the persistent
 * environments are filled through the admin API. Seeding is idempotent - a fixture already present
 * for an API is left alone, so a redeploy never overwrites what a team published.
 */
@Slf4j
@Configuration
public class FixtureSeeder {

    @Bean
    ApplicationRunner seedFixtures(final RecordingRepository recordings,
                                   final ApiRegistry registry,
                                   final ScenarioCatalog catalogue,
                                   final ObjectMapper mapper) {
        return args -> {
            final Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:fixtures/*.json");

            for (final Resource file : files) {
                try {
                    seed(file, recordings, registry, mapper);
                } catch (final Exception unreadable) {
                    // One bad fixture must not stop the service starting for every other API.
                    log.error("Could not seed fixture {}: {}",
                            file.getFilename(), unreadable.getMessage());
                }
            }
            catalogue.refreshAll();
        };
    }

    private void seed(final Resource file,
                      final RecordingRepository recordings,
                      final ApiRegistry registry,
                      final ObjectMapper mapper) throws Exception {

        final JsonNode root = mapper.readTree(file.getInputStream());
        final String apiId = root.path("apiId").asText();
        if (!registry.isRegistered(apiId)) {
            log.warn("Fixture {} names api '{}', which is not in the registry - skipped",
                    file.getFilename(), apiId);
            return;
        }

        for (final JsonNode node : root.path("scenarios")) {
            final String method = node.path("method").asText("GET");
            final String key = RequestKey.of(apiId, node.path("path").asText(), null);

            final boolean present = recordings.findByApiIdAndStatus(apiId, RecordingStatus.PUBLISHED)
                    .stream()
                    .anyMatch(existing -> existing.getMethod().equalsIgnoreCase(method)
                            && existing.getRequestKey().equals(key));
            if (present) {
                continue;
            }

            final RecordingEntity recording = new RecordingEntity();
            recording.setId(UUID.randomUUID());
            recording.setApiId(apiId);
            recording.setOperationId(node.path("operationId").asText());
            recording.setMethod(method);
            recording.setRequestKey(key);
            recording.setHttpStatus(node.path("status").asInt(200));
            recording.setBody(mapper.writeValueAsString(node.path("body")));
            recording.setResponseHeaders(null);
            recording.setStatus(RecordingStatus.PUBLISHED);
            recording.setScenarioId(node.path("scenarioId").asText(null));
            recording.setSummary(node.path("summary").asText(null));
            recording.setRecordedFrom("fixture");
            recording.setRecordedAt(OffsetDateTime.now());
            recording.setRecordedBy("fixture");
            recording.setPublishedAt(OffsetDateTime.now());
            recording.setPublishedBy("fixture");
            recordings.save(recording);
            log.info("Seeded fixture api:{} {} {}", apiId, method, key);
        }
    }
}
