package uk.gov.hmcts.amp.tin.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import uk.gov.hmcts.amp.tin.entity.RecordingEntity;
import uk.gov.hmcts.amp.tin.entity.RecordingStatus;
import uk.gov.hmcts.amp.tin.registry.TestRegistry;
import uk.gov.hmcts.amp.tin.repository.RecordingRepository;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The three promises in FixtureSeeder's Javadoc, each asserted:
 *
 * <ol>
 *   <li>an unchanged fixture writes nothing, so a redeploy is a no-op rather than churn;</li>
 *   <li>an edited fixture supersedes the row it replaces, so an edit takes effect without anyone
 *       dropping a table;</li>
 *   <li>a fixture never overwrites a recording that did not come from one, so a redeploy cannot
 *       silently undo what a team published through the admin API.</li>
 * </ol>
 *
 * <p>The third is the one with teeth. Until the admin API exists nothing exercises it in anger,
 * which is exactly why it needs a test rather than a comment.
 */
class FixtureSeedingServiceTest {

    private static final String FIXTURE_JSON = """
            {
              "apiId": "slc",
              "scenarios": [
                {
                  "scenarioId": "allocated",
                  "operationId": "getCourtScheduleByCaseUrn",
                  "method": "GET",
                  "path": "/slc/case/TIN-ALLOCATED-01/courtschedule",
                  "status": 200,
                  "summary": "An allocated case",
                  "body": {"courtSchedule": []}
                }
              ]
            }
            """;

    private RecordingRepository recordings;
    private FixtureSeeder.FixtureSeedingService seeding;

    @BeforeEach
    void setUp() {
        recordings = mock(RecordingRepository.class);
        seeding = new FixtureSeeder.FixtureSeedingService(
                recordings, TestRegistry.selfIssued(), new ObjectMapper());
    }

    private static Resource fixture(final String json) {
        return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "slc.json";
            }
        };
    }

    private RecordingEntity live(final String hash, final String recordedFrom) {
        final RecordingEntity existing = new RecordingEntity();
        existing.setId(UUID.randomUUID());
        existing.setApiId("slc");
        existing.setStatus(RecordingStatus.PUBLISHED);
        existing.setFixtureHash(hash);
        existing.setRecordedFrom(recordedFrom);
        return existing;
    }

    private String hashOfTheFixtureAbove() {
        return FixtureSeeder.FixtureSeedingService.hash(
                "getCourtScheduleByCaseUrn", "GET", "case/tin-allocated-01/courtschedule",
                200, "{\"courtSchedule\":[]}", "An allocated case");
    }

    @Test
    @DisplayName("a fixture with nothing live is seeded")
    void seedsWhenNothingIsLive() throws Exception {
        when(recordings.findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        seeding.seedFile(fixture(FIXTURE_JSON));

        verify(recordings).save(any(RecordingEntity.class));
        verify(recordings, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an unchanged fixture writes nothing at all")
    void unchangedFixtureIsANoOp() throws Exception {
        when(recordings.findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.of(live(hashOfTheFixtureAbove(), "fixture")));

        seeding.seedFile(fixture(FIXTURE_JSON));

        // Comparing a content hash rather than a timestamp is what makes a redeploy free. If this
        // starts writing, every deploy churns the table and every row's recordedAt becomes a lie.
        verify(recordings, never()).save(any());
        verify(recordings, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("an edited fixture archives the old row and writes a new one")
    void changedFixtureSupersedes() throws Exception {
        final RecordingEntity existing = live("a-stale-hash", "fixture");
        when(recordings.findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.of(existing));

        seeding.seedFile(fixture(FIXTURE_JSON));

        assertThat(existing.getStatus()).isEqualTo(RecordingStatus.ARCHIVED);
        verify(recordings).saveAndFlush(existing);
        verify(recordings).save(any(RecordingEntity.class));
    }

    @Test
    @DisplayName("a row that did not come from a fixture is left exactly where it is")
    void doesNotOverwriteANonFixtureRecording() throws Exception {
        final RecordingEntity published = live(null, "admin-api");
        when(recordings.findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.of(published));

        seeding.seedFile(fixture(FIXTURE_JSON));

        // The invariant that makes the admin API safe to build: a redeploy must not undo what
        // someone published. Note the hash differs, so this is NOT the no-op path - the guard that
        // stops it is recordedFrom, and this is the only test that distinguishes the two.
        assertThat(published.getStatus()).isEqualTo(RecordingStatus.PUBLISHED);
        verify(recordings, never()).save(any());
        verify(recordings, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a fixture naming an unregistered API is skipped, not seeded")
    void unregisteredApiIsSkipped() throws Exception {
        seeding.seedFile(fixture(FIXTURE_JSON.replace("\"slc\"", "\"nope\"")));

        verify(recordings, never()).save(any());
        verify(recordings, never())
                .findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any());
    }

    @Test
    @DisplayName("the seeded row carries the fixture provenance, so it can be superseded later")
    void seededRowIsMarkedAsAFixture() throws Exception {
        when(recordings.findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        seeding.seedFile(fixture(FIXTURE_JSON));

        final org.mockito.ArgumentCaptor<RecordingEntity> saved =
                org.mockito.ArgumentCaptor.forClass(RecordingEntity.class);
        verify(recordings).save(saved.capture());

        final RecordingEntity row = saved.getValue();
        assertThat(row.getApiId()).isEqualTo("slc");
        assertThat(row.getMethod()).isEqualTo("GET");
        assertThat(row.getRequestKey()).isEqualTo("case/tin-allocated-01/courtschedule");
        assertThat(row.getHttpStatus()).isEqualTo(200);
        assertThat(row.getStatus()).isEqualTo(RecordingStatus.PUBLISHED);
        assertThat(row.getRecordedFrom()).isEqualTo("fixture");
        assertThat(row.getFixtureHash()).isEqualTo(hashOfTheFixtureAbove());
    }

    @Test
    @DisplayName("seedAll loads the real committed fixtures without throwing")
    void seedAllReadsTheCommittedFixtures() throws Exception {
        when(recordings.findFirstByApiIdAndMethodAndRequestKeyAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        seeding.seedAll();

        // slc has seven and rcc four, all committed under src/main/resources/fixtures.
        verify(recordings, org.mockito.Mockito.atLeast(11)).save(any(RecordingEntity.class));
    }

    @Test
    @DisplayName("a malformed fixture throws out of seedFile, for seedAll to catch per file")
    void malformedFixturePropagatesSoSeedAllCanCatchIt() {
        // The tolerance lives one level up: seedFile propagates and seedAll catches per file, so
        // one bad fixture cannot leave the service with no scenarios at all. Asserting the throw
        // here is what pins that split - if seedFile ever swallowed this itself, a broken fixture
        // would seed nothing and log nothing.
        assertThatThrownBy(() -> seeding.seedFile(fixture("{ this is not json")))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(recordings);
    }

    @Test
    @DisplayName("the hash changes when any field a consumer can see changes")
    void hashCoversEveryVisibleField() {
        final String base = hashOfTheFixtureAbove();

        assertThat(FixtureSeeder.FixtureSeedingService.hash("other", "GET",
                "case/tin-allocated-01/courtschedule", 200, "{\"courtSchedule\":[]}", "An allocated case"))
                .isNotEqualTo(base);
        assertThat(FixtureSeeder.FixtureSeedingService.hash("getCourtScheduleByCaseUrn", "POST",
                "case/tin-allocated-01/courtschedule", 200, "{\"courtSchedule\":[]}", "An allocated case"))
                .isNotEqualTo(base);
        assertThat(FixtureSeeder.FixtureSeedingService.hash("getCourtScheduleByCaseUrn", "GET",
                "case/tin-allocated-01/courtschedule", 404, "{\"courtSchedule\":[]}", "An allocated case"))
                .isNotEqualTo(base);
        assertThat(FixtureSeeder.FixtureSeedingService.hash("getCourtScheduleByCaseUrn", "GET",
                "case/tin-allocated-01/courtschedule", 200, "{\"courtSchedule\":[1]}", "An allocated case"))
                .isNotEqualTo(base);
        // The summary is shown on /scenarios, so editing it must move the hash too or the change
        // appears to do nothing.
        assertThat(FixtureSeeder.FixtureSeedingService.hash("getCourtScheduleByCaseUrn", "GET",
                "case/tin-allocated-01/courtschedule", 200, "{\"courtSchedule\":[]}", "Different"))
                .isNotEqualTo(base);
    }

    @Test
    @DisplayName("a null summary hashes as an absent one, not as a crash")
    void nullSummaryHashesAsAbsent() {
        // A fixture may omit summary entirely, and asText(null) hands back null. The hash folds
        // null to "" deliberately, so adding an empty summary to a fixture is NOT a change and
        // does not re-seed. Both branches asserted because only one of them is the throwing one.
        assertThat(FixtureSeeder.FixtureSeedingService.hash("op", "GET", "k", 200, "{}", null))
                .isNotBlank()
                .isEqualTo(FixtureSeeder.FixtureSeedingService.hash("op", "GET", "k", 200, "{}", ""));
    }

    @Test
    @DisplayName("the seeding runner refreshes the catalogue after writing")
    void runnerRefreshesTheCatalogue() throws Exception {
        final FixtureSeeder.FixtureSeedingService service = mock(FixtureSeeder.FixtureSeedingService.class);
        final uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog catalogue =
                mock(uk.gov.hmcts.amp.tin.scenarios.ScenarioCatalog.class);

        new FixtureSeeder().seedFixtures(service, catalogue).run(null);

        // Seeding without refreshing would leave the in-memory catalogue empty until the next
        // restart, so /scenarios would report nothing while the rows were already there.
        verify(service).seedAll();
        verify(catalogue).refreshAll();
    }
}
