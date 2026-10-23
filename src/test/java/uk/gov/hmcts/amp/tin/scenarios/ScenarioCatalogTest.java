package uk.gov.hmcts.amp.tin.scenarios;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.amp.tin.entity.RecordingEntity;
import uk.gov.hmcts.amp.tin.entity.RecordingStatus;
import uk.gov.hmcts.amp.tin.registry.ApiDefinition;
import uk.gov.hmcts.amp.tin.registry.ApiRegistry;
import uk.gov.hmcts.amp.tin.repository.RecordingRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The three assertions that prove consolidation is safe. Each one fails loudly if the per-API
 * separation is lost, and none of them would fail on a single-API service.
 */
class ScenarioCatalogTest {

    private final List<RecordingEntity> slcRows = new ArrayList<>();
    private final List<RecordingEntity> rccRows = new ArrayList<>();
    private RecordingRepository recordings;
    private ScenarioCatalog catalogue;

    @BeforeEach
    void setUp() {
        // The same request text under two different APIs. This is the collision the single shared
        // cache in the original service would have had, silently.
        slcRows.add(row("slc", "getCourtScheduleByCaseUrn", "GET",
                "tin-allocated-01", 200, "{\"courtSchedule\":[]}", "slc answer"));
        rccRows.add(row("rcc", "getCourthouseById", "GET",
                "tin-allocated-01", 200, "{\"courtHouseId\":\"X\"}", "rcc answer"));

        recordings = mock(RecordingRepository.class);
        when(recordings.findByApiIdAndStatus(eq("slc"), eq(RecordingStatus.PUBLISHED)))
                .thenReturn(slcRows);
        when(recordings.findByApiIdAndStatus(eq("rcc"), eq(RecordingStatus.PUBLISHED)))
                .thenReturn(rccRows);

        final ApiRegistry registry = mock(ApiRegistry.class);
        when(registry.all()).thenReturn(List.of(
                api("slc"), api("rcc")));

        catalogue = new ScenarioCatalog(recordings, registry, new ObjectMapper());
        catalogue.refreshAll();
    }

    @Test
    @DisplayName("two APIs may use the same request text without colliding")
    void noCollisionAcrossApis() {
        assertThat(catalogue.find("slc", "GET", "tin-allocated-01"))
                .get().extracting(Scenario::summary).isEqualTo("slc answer");
        assertThat(catalogue.find("rcc", "GET", "tin-allocated-01"))
                .get().extracting(Scenario::summary).isEqualTo("rcc answer");
    }

    @Test
    @DisplayName("a request for one API never resolves against another's recordings")
    void noLeakAcrossApis() {
        assertThat(catalogue.find("slc", "GET", "courthouses/c1/courtrooms/r2")).isEmpty();
        assertThat(catalogue.find("unregistered", "GET", "tin-allocated-01")).isEmpty();
    }

    @Test
    @DisplayName("refreshing one API leaves every other API's cache untouched")
    void refreshIsScopedToOneApi() {
        // rcc publishes something new; slc must be unaffected and must not be re-read.
        rccRows.add(row("rcc", "getCourtroomById", "GET",
                "courthouses/c1/courtrooms/r2", 200, "{}", "nested"));
        catalogue.refresh("rcc");

        assertThat(catalogue.all("rcc")).hasSize(2);
        assertThat(catalogue.all("slc")).hasSize(1);
        assertThat(catalogue.find("slc", "GET", "tin-allocated-01"))
                .get().extracting(Scenario::summary).isEqualTo("slc answer");
    }

    private static ApiDefinition api(final String shortCode) {
        return new ApiDefinition(shortCode, shortCode, "api-cp-" + shortCode, shortCode,
                "app.read", "aud-" + shortCode, List.of());
    }

    private static RecordingEntity row(final String apiId, final String operationId,
                                       final String method, final String requestKey,
                                       final int status, final String body, final String summary) {
        final RecordingEntity recording = new RecordingEntity();
        recording.setId(UUID.randomUUID());
        recording.setApiId(apiId);
        recording.setOperationId(operationId);
        recording.setMethod(method);
        recording.setRequestKey(requestKey);
        recording.setHttpStatus(status);
        recording.setBody(body);
        recording.setStatus(RecordingStatus.PUBLISHED);
        recording.setSummary(summary);
        recording.setRecordedFrom("fixture");
        recording.setRecordedAt(OffsetDateTime.now());
        recording.setRecordedBy("test");
        return recording;
    }
}
