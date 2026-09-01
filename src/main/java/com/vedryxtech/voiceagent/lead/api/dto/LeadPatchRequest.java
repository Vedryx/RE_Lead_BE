package com.vedryxtech.voiceagent.lead.api.dto;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
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
        Boolean doNotCall
) {
}
