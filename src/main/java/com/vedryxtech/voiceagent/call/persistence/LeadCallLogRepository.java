package com.vedryxtech.voiceagent.call.persistence;

import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeadCallLogRepository extends MongoRepository<LeadCallLog, ObjectId> {

    List<LeadCallLog> findByLeadIdOrderByAttemptNumberDesc(ObjectId leadId);

    Optional<LeadCallLog> findFirstByLeadIdOrderByAttemptNumberDesc(ObjectId leadId);

    Optional<LeadCallLog> findByIdempotencyKey(String idempotencyKey);

    /** The attempt an egress webhook is talking about, found by where it wrote the file. */
    Optional<LeadCallLog> findByRecordingKey(String recordingKey);

    /** Attempts already made for a lead since a point in time - enforces the per-day cap. */
    long countByLeadIdAndDialStartedAtGreaterThanEqual(ObjectId leadId, OffsetDateTime since);
}
