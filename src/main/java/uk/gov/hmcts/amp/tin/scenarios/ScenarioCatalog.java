package uk.gov.hmcts.amp.tin.scenarios;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.amp.tin.entity.RecordingEntity;
import uk.gov.hmcts.amp.tin.entity.RecordingStatus;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.repository.RecordingRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The published scenarios, cached in memory, <b>per API</b>.
 *
 * <p>The per-API part is load bearing and is the thing that does not matter at two APIs and decides
 * whether this holds at a hundred. A single shared cache rebuilt on every publish would mean one
 * team's publish rebuilding every other team's scenarios, every published body held at once, and one
 * malformed row failing the refresh for everybody.
 *
 * <p>The cache key is the request key alone <i>within</i> an API's map. Two APIs may legitimately
 * use the same URN text - keying globally on the request key would silently collide them.
 */
@Slf4j
@Service
public class ScenarioCatalog {

    private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() {
    };

    private final RecordingRepository recordings;
    private final ApiRegistry registry;
    private final ObjectMapper mapper;

    /** api id -> (request key -> scenario). Replaced wholesale per API, never mutated in place. */
    private final Map<String, Map<String, Scenario>> byApi = new ConcurrentHashMap<>();

    public ScenarioCatalog(final RecordingRepository recordings,
                           final ApiRegistry registry,
                           final ObjectMapper mapper) {
        this.recordings = recordings;
        this.registry = registry;
        this.mapper = mapper;
    }

    /** Rebuilds every registered API. Startup only. */
    public void refreshAll() {
        registry.all().forEach(api -> refresh(api.shortCode()));
    }

    /**
     * Rebuilds one API's cache from the store. Called after a publish for that API, and nothing
     * else is touched.
     */
    public void refresh(final String apiId) {
        final String key = apiId.toLowerCase(Locale.ROOT);
        final Map<String, Scenario> rebuilt = new LinkedHashMap<>();
        for (final RecordingEntity recording
                : recordings.findByApiIdAndStatus(key, RecordingStatus.PUBLISHED)) {
            rebuilt.put(cacheKey(recording.getMethod(), recording.getRequestKey()),
                    materialise(recording));
        }
        byApi.put(key, Map.copyOf(rebuilt));
        log.info("Scenario catalogue refreshed for api:{} scenarios:{}", key, rebuilt.size());
    }

    /** The default way in: what was recorded for this exact request. */
    public Optional<Scenario> find(final String apiId, final String method, final String requestKey) {
        return Optional.ofNullable(forApi(apiId).get(cacheKey(method, requestKey)));
    }

    /**
     * Everything published for one API, for {@code GET /{shortCode}/scenarios}.
     */
    public List<Scenario> all(final String apiId) {
        return List.copyOf(forApi(apiId).values());
    }

    private Map<String, Scenario> forApi(final String apiId) {
        if (apiId == null) {
            return Map.of();
        }
        return byApi.getOrDefault(apiId.toLowerCase(Locale.ROOT), Map.of());
    }

    private static String cacheKey(final String method, final String requestKey) {
        return method.toUpperCase(Locale.ROOT) + " " + requestKey;
    }

    private Scenario materialise(final RecordingEntity recording) {
        return new Scenario(
                recording.getScenarioId(),
                recording.getApiId(),
                recording.getOperationId(),
                recording.getMethod(),
                recording.getRequestKey(),
                recording.getHttpStatus(),
                recording.getBody(),
                headers(recording),
                recording.getSummary(),
                recording.getRecordedFrom());
    }

    private Map<String, String> headers(final RecordingEntity recording) {
        final String raw = recording.getResponseHeaders();
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(raw, HEADERS);
        } catch (final Exception malformed) {
            // One bad row must not take the whole API's refresh down with it.
            log.warn("Recording {} has unreadable response_headers; serving none", recording.getId());
            return Map.of();
        }
    }

    ApiDefinition requireApi(final String shortCode) {
        return registry.byShortCode(shortCode)
                .orElseThrow(() -> new IllegalArgumentException("No such API: " + shortCode));
    }
}
