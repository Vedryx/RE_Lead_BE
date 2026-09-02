package com.vedryxtech.voiceagent.call.api.dto;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * What the agent needs to know before it says a word.
 *
 * <p>Sent with the claim rather than fetched separately: the dialler is about to place a
 * call, and a round trip here is silence on a live line.
 *
 * <p>Without this the agent opens call three exactly like call one — "you enquired about
 * the project, are you looking at 2 BHK or 3 BHK?" — to someone who has answered that
 * twice. Nothing sounds more like a machine.
 */
@Schema(description = "What is already known about this lead, so the agent does not start over")
public record CallContext(

        String project,

        @Schema(description = "Where the lead is in the funnel")
        LeadStage stage,

        @Schema(description = "What we owe them, if anything", example = "followUpCall")
        ActionType pendingAction,

        @Schema(description = "The site visit, if one is booked")
        OffsetDateTime scheduledFor,

        @Schema(description = "The callback, if one was promised")
        OffsetDateTime callbackAt,

        @Schema(description = "What they last asked about")
        String query,

        String whatsappPhone,

        @Schema(description = "How many times we have tried, including this call")
        int previousAttempts,

        @Schema(description = "How many of those actually connected")
        int previousConnects,

        @Schema(description = "The last three calls, most recent first. Capped so the "
                + "prompt stays bounded; older calls remain on GET /leads/{id}/calls.")
        List<PriorCall> priorCalls
) {

    /** One earlier conversation, in the least the agent needs to sound like it remembers. */
    public record PriorCall(
            OffsetDateTime endedAt,
            CallOutcome outcome,
            CallDisposition disposition,
            String summary
    ) {
    }
}
