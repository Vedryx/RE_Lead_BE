package com.vedryxtech.voiceagent.call.api.dto;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallEvent;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(description = "Never written. Declared on the document and mapped here, "
                + "but setTranscriptUrl has no callers — the agent keeps no transcript "
                + "and the summary is the record of what was said.")
        String transcriptUrl,
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
