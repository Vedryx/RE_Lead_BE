package com.vedryxtech.voiceagent.lead.api.dto;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Partial update payload for {@code PATCH /api/v1/leads/{id}}.
 *
 * <p>Every field is optional; a field left out (or sent as {@code null}) is kept as-is.
 * To clear a field, use {@code PUT} with the full payload.</p>
 */
public record LeadPatchRequest(

        @Size(max = 200)
        String project,

        ActionType actionType,

        LeadStatus status,

        @Schema(description = "Move the lead by hand. Not ratcheted: a person may move "
                + "it backwards to correct a mistake.", example = "siteVisit")
        LeadStage stage,

        @Schema(description = "Why a lead was discarded. The stage says where in the "
                + "funnel; this says why.", example = "notInterested")
        LeadFinalStatus finalStatus,

        @Size(max = 150)
        String name,

        @Pattern(regexp = "^[0-9+() -]{7,25}$", message = "phone must be 7-25 characters of digits, +, spaces, dashes or brackets")
        String phone,

        @Pattern(regexp = "^[0-9+() -]{7,25}$", message = "callingPhone must be 7-25 characters of digits, +, spaces, dashes or brackets")
        String callingPhone,

        @Pattern(regexp = "^[0-9+() -]{7,25}$", message = "whatsappPhone must be 7-25 characters of digits, +, spaces, dashes or brackets")
        String whatsappPhone,

        @Size(max = 2000)
        String query,

        @Size(max = 2000)
        String notes,

        OffsetDateTime callbackAt,

        OffsetDateTime scheduledFor,

        OffsetDateTime reminderDueAt,

        Boolean reminderEnabled,

        Boolean confirmedByLead,

        @Size(max = 100)
        String source,

        @Size(max = 100)
        String campaign,

        @Size(max = 64)
        String assignedTo,

        /** Suppress the lead from all future dialling. */
        Boolean doNotCall,

        /**
         * Why this lead is being made callable again after asking not to be.
         *
         * <p>Required to clear {@code doNotCall}, and only then. Someone asked not to be
         * called; undoing that should take a deliberate act and leave a record, not a
         * stray {@code false} in a form submission that also changed their name.
         */
        @Size(max = 300)
        String doNotCallClearedReason
) {
}
