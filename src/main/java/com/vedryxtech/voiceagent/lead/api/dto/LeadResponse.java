package com.vedryxtech.voiceagent.lead.api.dto;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;

import java.time.OffsetDateTime;

/**
 * Lead as returned by the API.
 *
 * <p>{@code id} is the only identifier. On a fresh lead every field below {@code do_not_call}
 * is null - they are filled in when the call outcome is reported.</p>
 */
public record LeadResponse(
        String id,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String project,
        ActionType actionType,
        LeadStatus status,
        String name,
        String phone,
        String callingPhone,
        String whatsappPhone,
        String query,
        String notes,
        OffsetDateTime callbackAt,
        OffsetDateTime scheduledFor,
        OffsetDateTime reminderDueAt,
        Boolean reminderEnabled,
        Boolean confirmedByLead,

        // call pipeline
        LeadPipelineStatus pipelineStatus,
        LeadFinalStatus finalStatus,
        LeadStage stage,
        CallDisposition lastDisposition,
        CallOutcome lastOutcome,
        Integer attemptCount,
        Integer connectedCount,
        OffsetDateTime lastAttemptAt,
        OffsetDateTime lastConnectedAt,
        OffsetDateTime nextAttemptAt,
        Integer totalTalkSeconds,
        String lastCallLogId,
        Boolean doNotCall,
        String assignedTo,
        String source,
        String campaign,

        String lastRecordingUrl
) {
}
