package com.vedryxtech.voiceagent.call.api.dto;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallEvent;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;

import io.swagger.v3.oas.annotations.media.Schema;
import com.vedryxtech.voiceagent.call.domain.TranscriptTurn;
import java.time.OffsetDateTime;
import java.util.List;

/** One row of the follow-up history, including the recording the dashboard plays. */
public record CallLogResponse(
        String id,
        String leadId,
        String name,
        String phone,
        String project,
        Integer attemptNumber,
        String direction,
        String handledBy,
        CallOutcome outcome,
        CallDisposition disposition,
        LeadPipelineStatus pipelineStatusBefore,
        LeadPipelineStatus pipelineStatusAfter,
        OffsetDateTime queuedAt,
        OffsetDateTime dialStartedAt,
        OffsetDateTime answeredAt,
        OffsetDateTime endedAt,
        Integer ringSeconds,
        Integer talkSeconds,
        RecordingStatus recordingStatus,
        String recordingUrl,
        Integer recordingDurationSeconds,
        @Schema(description = "Object key of the archived transcript. Not a URL you can "
                + "open — call GET /calls/{id}/recording for a signed link.")
        String transcriptKey,

        @Schema(description = "What was said, oldest first. Redacted by the agent before "
                + "it was stored: card numbers, account numbers and one-time codes "
                + "never leave the call.")
        List<TranscriptTurn> transcript,

        @Schema(description = "Turns before truncation. Larger than transcript.size() "
                + "means the stored copy is trimmed.")
        Integer transcriptTurnCount,
        String summary,
        String notes,
        OffsetDateTime requestedCallbackAt,
        OffsetDateTime retryScheduledFor,
        String errorCode,
        String errorMessage,
        List<CallEvent> events,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
