package com.vedryxtech.voiceagent.storage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.domain.TranscriptTurn;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The archive half of a call's record: what goes to object storage, and how it is read back.
 *
 * <p>Mongo holds the transcript too, and that is not redundant. R2 is the archive — it
 * sits beside the audio and outlives the database. Mongo is the index: "which leads asked
 * about Wakad" is a query over embedded turns and is impossible against a JSON blob behind
 * a signed URL.
 *
 * <p>Every method here tolerates storage being switched off or unreachable. A failed
 * archive write must never fail the outcome that produced it — the call happened, the
 * transcript is in Mongo, and a missing copy in R2 is a smaller loss than a 500 that sends
 * the agent's outcome back to the outbox for a reason it cannot fix by retrying.
 */
@Service
public class CallArtifactService {

    private static final Logger log = LoggerFactory.getLogger(CallArtifactService.class);

    private final ObjectStorageProperties properties;
    private final Optional<CallArtifactStore> store;
    private final ObjectMapper objectMapper;

    public CallArtifactService(ObjectStorageProperties properties,
                               Optional<CallArtifactStore> store,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.isEnabled() && store.isPresent();
    }

    /**
     * Decide where this call's artifacts belong and stamp it on the log.
     *
     * <p>Called when the attempt is opened, before anything has been recorded, because the
     * agent needs the audio key in order to tell egress where to write.
     */
    public void assignKeys(LeadCallLog callLog, String project) {
        OffsetDateTime at = callLog.getCreatedAt() != null
                ? callLog.getCreatedAt()
                : OffsetDateTime.now(ZoneOffset.UTC);
        String prefix = CallArtifactKeys.prefix(
                properties.getPrefix(), project, at, callLog.getIdAsString());

        callLog.setRecordingPrefix(prefix);
        callLog.setRecordingKey(CallArtifactKeys.audioKey(prefix));
        callLog.setTranscriptKey(CallArtifactKeys.transcriptKey(prefix));
    }

    /**
     * Put the transcript in the archive, beside the audio.
     *
     * <p>Written by this service rather than by the agent, deliberately. It keeps the
     * agent's R2 credential write-only for the audio prefix, keeps one writer to reason
     * about, and — the reason that matters — lets the transcript ride the outcome POST,
     * so it inherits the agent's outbox instead of needing a retry path of its own.
     */
    public void archiveTranscript(LeadCallLog callLog, List<TranscriptTurn> turns) {
        if (!isEnabled() || turns == null || turns.isEmpty() || callLog.getTranscriptKey() == null) {
            return;
        }
        try {
            store.orElseThrow().putJson(callLog.getTranscriptKey(), render(callLog, turns));
        } catch (JsonProcessingException | RuntimeException ex) {
            // Mongo already has the turns. Losing the archive copy is worth a loud log and
            // nothing more; failing the request would bounce a good outcome into the outbox.
            log.error("Could not archive the transcript for call {} at {}: {}",
                    callLog.getIdAsString(), callLog.getTranscriptKey(), ex.getMessage());
        }
    }

    /** A link per artifact, minted now, valid for the configured window. */
    public Map<String, String> linksFor(LeadCallLog callLog) {
        Map<String, String> links = new LinkedHashMap<>();
        if (!isEnabled()) {
            return links;
        }
        try {
            CallArtifactStore live = store.orElseThrow();
            List<String> present = callLog.getRecordingPrefix() == null
                    ? List.of()
                    : live.list(callLog.getRecordingPrefix());
            if (present.contains(callLog.getRecordingKey())) {
                links.put("audioUrl", live.presignedGet(callLog.getRecordingKey()));
            }
            if (present.contains(callLog.getTranscriptKey())) {
                links.put("transcriptUrl", live.presignedGet(callLog.getTranscriptKey()));
            }
        } catch (RuntimeException ex) {
            log.error("Could not mint links for call {}: {}", callLog.getIdAsString(), ex.getMessage());
        }
        return links;
    }

    /**
     * The transcript as it is archived: self-describing, so a file found in a bucket in
     * two years can be read without the database that produced it.
     */
    private String render(LeadCallLog callLog, List<TranscriptTurn> turns)
            throws JsonProcessingException {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("callLogId", callLog.getIdAsString());
        document.put("leadId", callLog.getLeadId() == null ? null : callLog.getLeadId().toHexString());
        document.put("project", callLog.getProject());
        document.put("phone", callLog.getPhone());
        document.put("startedAt", callLog.getDialStartedAt());
        document.put("endedAt", callLog.getEndedAt());
        document.put("outcome", callLog.getOutcome());
        document.put("disposition", callLog.getDisposition());
        document.put("summary", callLog.getSummary());
        document.put("turnCount", callLog.getTranscriptTurnCount());
        document.put("redacted", true);
        document.put("turns", turns.stream().map(turn -> Map.of(
                "role", String.valueOf(turn.getRole()),
                "text", String.valueOf(turn.getText()),
                "atSeconds", turn.getAtSeconds() == null ? 0 : turn.getAtSeconds())).toList());
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
    }
}
