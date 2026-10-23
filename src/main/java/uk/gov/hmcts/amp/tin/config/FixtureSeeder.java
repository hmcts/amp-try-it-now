package uk.gov.hmcts.amp.tin.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.amp.tin.entity.RecordingEntity;
import uk.gov.hmcts.amp.tin.entity.RecordingStatus;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.repository.RecordingRepository;
import uk.gov.hmcts.amp.tin.scenarios.RequestKey;
import uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Seeds committed fixtures, then builds the catalogue.
 *
 * <p>Fixtures and the admin API are two mechanisms with two jobs. Preview and the AAT staging slot
 * are destroyed on every run and can only ever start from committed seeds; the persistent
 * environments are filled through the admin API.
 *
 * <p><b>Seeding is driven by a content hash.</b> A fixture whose hash matches the live row is left
 * alone, so an unchanged deployment writes nothing; an edited fixture supersedes the row it
 * replaces. Comparing a hash rather than a timestamp is what makes a redeploy a no-op instead of
 * churn, and what makes an edit take effect without anyone dropping a table.
 *
 * <p><b>A fixture never overwrites a recording that did not come from one.</b> A row ingested
 * through the admin API has a null hash and is left exactly where it is - a redeploy must not
 * silently undo what a team published.
 */
@Slf4j
@Configuration
public class FixtureSeeder {

    /**
     * The provenance marker on a seeded row. A fixture may only ever supersede another fixture,
     * so this exact value is both what seeding writes and what it checks before overwriting.
     */
    private static final String FIXTURE = "fixture";

    @Bean
    /* default */ ApplicationRunner seedFixtures(final FixtureSeedingService seeding,
                                   final ScenarioCatalog catalogue) {
        return args -> {
            seeding.seedAll();
            catalogue.refreshAll();
        };
    }

    /** Separated so the seeding of one fixture file runs in its own transaction. */
    @Slf4j
    @org.springframework.stereotype.Service
    public static class FixtureSeedingService {

        private final RecordingRepository recordings;
        private final ApiRegistry registry;
        private final ObjectMapper mapper;

        public FixtureSeedingService(final RecordingRepository recordings,
                                     final ApiRegistry registry,
                                     final ObjectMapper mapper) {
            this.recordings = recordings;
            this.registry = registry;
            this.mapper = mapper;
        }

        public void seedAll() throws Exception {
            final Resource[] files = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:fixtures/*.json");
            for (final Resource file : files) {
                try {
                    seedFile(file);
                } catch (final Exception unreadable) {
                    // One bad fixture must not stop the service starting for every other API.
                    log.error("Could not seed fixture {}: {}",
                            file.getFilename(), unreadable.getMessage());
                }
            }
        }

        @Transactional
        public void seedFile(final Resource file) throws Exception {
            final JsonNode root = mapper.readTree(file.getInputStream());
            final String apiId = root.path("apiId").asText();
            if (!registry.isRegistered(apiId)) {
                log.warn("Fixture {} names api '{}', which is not in the registry - skipped",
                        file.getFilename(), apiId);
                return;
            }
            for (final JsonNode node : root.path("scenarios")) {
                seedScenario(apiId, node);
            }
        }

        private void seedScenario(final String apiId, final JsonNode node) throws Exception {
            final String method = node.path("method").asText("GET");
            final String key = RequestKey.from(apiId, node.path("path").asText(), null);
            final String body = mapper.writeValueAsString(node.path("body"));
            final int status = node.path("status").asInt(200);
            final String summary = node.path("summary").asText(null);
            final String operationId = node.path("operationId").asText();
            final String scenarioId = node.path("scenarioId").asText(null);
            final String hash = hash(operationId, method, key, status, body, summary);

            final Optional<RecordingEntity> live = recordings
                    .findFirstByApiIdAndMethodAndRequestKeyAndStatus(
                            apiId, method, key, RecordingStatus.PUBLISHED);

            if (live.isPresent()) {
                final RecordingEntity existing = live.get();
                if (hash.equals(existing.getFixtureHash())) {
                    return;
                }
                // Provenance and change-detection are different questions. recorded_from says
                // where a row came from; the hash says whether its source has moved since. A row
                // that did not come from a fixture is never superseded by one, whatever its hash -
                // a redeploy must not undo what a team published.
                if (!FIXTURE.equals(existing.getRecordedFrom())) {
                    log.info("Leaving api:{} {} {} alone - recorded_from is '{}', not a fixture",
                            apiId, method, key, existing.getRecordedFrom());
                    return;
                }
                // Archive before inserting: the partial unique index permits one PUBLISHED row.
                existing.setStatus(RecordingStatus.ARCHIVED);
                recordings.saveAndFlush(existing);
                log.info("Fixture changed for api:{} {} {} - superseding", apiId, method, key);
            }

            final RecordingEntity recording = new RecordingEntity();
            recording.setId(UUID.randomUUID());
            recording.setApiId(apiId);
            recording.setOperationId(operationId);
            recording.setMethod(method);
            recording.setRequestKey(key);
            recording.setHttpStatus(status);
            recording.setBody(body);
            recording.setStatus(RecordingStatus.PUBLISHED);
            recording.setScenarioId(scenarioId);
            recording.setSummary(summary);
            recording.setFixtureHash(hash);
            recording.setRecordedFrom(FIXTURE);
            recording.setRecordedAt(OffsetDateTime.now());
            recording.setRecordedBy(FIXTURE);
            recording.setPublishedAt(OffsetDateTime.now());
            recording.setPublishedBy(FIXTURE);
            recordings.save(recording);
            log.info("Seeded fixture api:{} {} {}", apiId, method, key);
        }

        /**
         * Over everything a consumer can observe. Changing a summary is a change - it shows on
         * {@code /scenarios} - so it has to move the hash too, or an edit would appear to do nothing.
         */
        /* default */ static String hash(final String operationId, final String method, final String key,
                           final int status, final String body, final String summary) {
            final String canonical = String.join("\u0000",
                    operationId, method, key, String.valueOf(status), body,
                    summary == null ? "" : summary);
            try {
                final byte[] digest = MessageDigest.getInstance("SHA-256")
                        .digest(canonical.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(digest);
            } catch (final Exception impossible) {
                throw new IllegalStateException("SHA-256 unavailable", impossible);
            }
        }
    }
}
