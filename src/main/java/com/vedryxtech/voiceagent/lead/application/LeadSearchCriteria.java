package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;

import java.time.OffsetDateTime;

/**
 * Optional filters for {@code GET /api/v1/leads}. A null field means "do not filter".
 * Single-tenant: there is no tenant scope to apply.
 */
public record LeadSearchCriteria(
        String project,
        ActionType actionType,
        LeadStatus status,
        LeadPipelineStatus pipelineStatus,
        LeadFinalStatus finalStatus,
        LeadStage stage,
        CallDisposition disposition,
        String phone,
        String name,
        String assignedTo,
        Boolean confirmedByLead,
        Boolean hasRecording,
        OffsetDateTime createdFrom,
        OffsetDateTime createdTo,
        OffsetDateTime scheduledFrom,
        OffsetDateTime scheduledTo,
        OffsetDateTime callbackBefore
) {
}
