package uk.gov.hmcts.amp.tin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** One captured example. See V1.001 for why the body is stored as text and served unchanged. */
@Entity
@Table(name = "recording")
@Getter
@Setter
@NoArgsConstructor
public class RecordingEntity {

    @Id
    private UUID id;

    @Column(name = "api_id")
    private String apiId;

    @Column(name = "operation_id")
    private String operationId;

    private String method;

    @Column(name = "request_key")
    private String requestKey;

    @Column(name = "http_status")
    private int httpStatus;

    private String body;

    @Column(name = "response_headers")
    private String responseHeaders;

    @Enumerated(EnumType.STRING)
    private RecordingStatus status;

    @Column(name = "scenario_id")
    private String scenarioId;

    private String summary;

    @Column(name = "fixture_hash")
    private String fixtureHash;

    @Column(name = "recorded_from")
    private String recordedFrom;

    @Column(name = "recorded_at")
    private OffsetDateTime recordedAt;

    @Column(name = "recorded_by")
    private String recordedBy;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "published_by")
    private String publishedBy;
}
