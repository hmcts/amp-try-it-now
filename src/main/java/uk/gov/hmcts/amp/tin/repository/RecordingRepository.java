package uk.gov.hmcts.amp.tin.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.hmcts.amp.tin.entity.RecordingEntity;
import uk.gov.hmcts.amp.tin.entity.RecordingStatus;

import java.util.List;
import java.util.UUID;

public interface RecordingRepository extends JpaRepository<RecordingEntity, UUID> {

    /**
     * Scoped to one API on purpose. A refresh triggered by one team publishing must not read, and
     * must not be able to fail on, another team's rows.
     */
    List<RecordingEntity> findByApiIdAndStatus(String apiId, RecordingStatus status);

    List<RecordingEntity> findByStatus(RecordingStatus status);
}
