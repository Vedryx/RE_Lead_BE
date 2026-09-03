package com.vedryxtech.voiceagent.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedryxtech.voiceagent.call.domain.CallEvent;
import com.vedryxtech.voiceagent.call.domain.CallEventType;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.call.persistence.LeadCallLogRepository;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/**
 * Closes the loop on a recording.
 *
 * <p>The call ends and its outcome is reported while the audio is still being
 * finalised, so a call log reaches {@code recording} or {@code processing} and stops
 * there. Nothing else ever moves it: this webhook is the only thing that knows the
 * file is written, how long it runs and how big it is.
 *
 * <p>Until this existed every recording sat at {@code starting} for ever and the
 * dashboard could not offer a play button for audio that was sitting in the bucket.
 */
@Service
public class LiveKitWebhookService {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookService.class);

    private static final String EGRESS_ENDED = "egress_ended";

    private final LeadCallLogRepository callLogRepository;
    private final LeadRepository leadRepository;
    private final ObjectMapper objectMapper;

    public LiveKitWebhookService(LeadCallLogRepository callLogRepository,
                                 LeadRepository leadRepository,
                                 ObjectMapper objectMapper) {
        this.callLogRepository = callLogRepository;
        this.leadRepository = leadRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Apply one webhook. Returns what was done, for the log line only.
     *
     * <p>Every unknown event is accepted and ignored. LiveKit retries anything it does
     * not get a 2xx for, so answering an error to {@code track_published} would earn a
     * retry storm for an event this service has no opinion about.
     */
    public String apply(String rawBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            log.warn("Webhook body was not JSON: {}", ex.getMessage());
            return "unparseable";
        }

        String event = root.path("event").asText("");
        if (!EGRESS_ENDED.equals(event)) {
            return "ignored " + (event.isBlank() ? "unnamed event" : event);
        }

        Optional<EgressResult> parsed = readEgress(root.path("egressInfo"));
        if (parsed.isEmpty()) {
            log.warn("egress_ended carried no file location; nothing to match on");
            return "no file in egressInfo";
        }
        EgressResult result = parsed.get();

        Optional<LeadCallLog> found = callLogRepository.findByRecordingKey(result.recordingKey());
        if (found.isEmpty()) {
            // Not necessarily wrong: an egress from another environment sharing the
            // bucket, or a call whose log has since been removed.
            log.warn("No call log for recording {}", result.recordingKey());
            return "no call log for " + result.recordingKey();
        }

        LeadCallLog callLog = found.get();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (result.failed()) {
            callLog.setRecordingStatus(RecordingStatus.FAILED);
            callLog.addEvent(CallEvent.of(CallEventType.RECORDING_READY,
                    "Egress failed: " + result.error()));
        } else {
            callLog.setRecordingStatus(RecordingStatus.AVAILABLE);
            callLog.setRecordingReadyAt(now);
            // Duration is advisory. LiveKit has been seen reporting a negative one with
            // no endedAt, and a negative length renders as a broken player rather than
            // an obviously wrong number.
            if (result.durationSeconds() != null && result.durationSeconds() > 0) {
                callLog.setRecordingDurationSeconds(result.durationSeconds());
            } else if (callLog.getTalkSeconds() != null && callLog.getTalkSeconds() > 0) {
                callLog.setRecordingDurationSeconds(callLog.getTalkSeconds());
            }
            if (result.sizeBytes() != null && result.sizeBytes() > 0) {
                callLog.setRecordingSizeBytes(result.sizeBytes());
            }
            callLog.addEvent(CallEvent.of(CallEventType.RECORDING_READY,
                    "Recording available (" + result.durationSeconds() + "s)"));
        }
        callLog.setUpdatedAt(now);
        callLogRepository.save(callLog);

        log.info("Recording for call {} is {}", callLog.getIdAsString(), callLog.getRecordingStatus());
        return callLog.getIdAsString() + " -> " + callLog.getRecordingStatus().getValue();
    }

    /**
     * Pull the one file this egress produced out of the event.
     *
     * <p>Audio-only egress writes a single file; {@code fileResults} is still an array,
     * and the first entry is the recording. The manifest LiveKit writes alongside it is
     * not listed there.
     */
    private Optional<EgressResult> readEgress(JsonNode info) {
        if (info.isMissingNode()) {
            return Optional.empty();
        }
        String status = info.path("status").asText("");
        String error = info.path("error").asText("");
        boolean failed = !error.isBlank() || status.contains("FAILED") || status.contains("ABORTED");

        JsonNode files = info.path("fileResults");
        JsonNode file = files.isArray() && !files.isEmpty() ? files.get(0) : info.path("file");
        String key = firstNonBlank(file.path("filename").asText(""), file.path("location").asText(""));
        if (key.isBlank()) {
            return Optional.empty();
        }

        // A location may arrive as a full URL; the stored key is the path within the
        // bucket, so keep everything after the bucket name.
        key = key.replaceFirst("^https?://[^/]+/", "");
        key = key.replaceFirst("^[^/]+/(?=recordings/)", "");

        Integer duration = null;
        JsonNode durationNode = file.path("duration");
        if (durationNode.isNumber()) {
            // Reported in nanoseconds by the protocol; anything smaller is already seconds.
            long raw = durationNode.asLong();
            duration = raw > 1_000_000L ? (int) (raw / 1_000_000_000L) : (int) raw;
        }
        Long size = file.path("size").isNumber() ? file.path("size").asLong() : null;

        return Optional.of(new EgressResult(key, duration, size, failed, error));
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }
}
