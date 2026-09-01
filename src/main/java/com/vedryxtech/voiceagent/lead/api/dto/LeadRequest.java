package com.vedryxtech.voiceagent.lead.api.dto;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * A lead to call.
 *
 * <p><b>Creating a fresh lead needs only a name and a phone number.</b> Everything under
 * "filled in after the call" is optional, because before the first call there is nothing to
 * put there - the agent reports it through
 * {@code POST /api/v1/calls/{callLogId}/outcome} once it has spoken to them.</p>
 *
 * <p>Those fields are still accepted here so an already-worked lead can be imported. When an
 * {@code actionType} is supplied, its matching time is required:</p>
 * <ul>
 *   <li>{@code teamCallback} needs {@code callbackAt}</li>
 *   <li>{@code siteVisit} / {@code followUpCall} need {@code scheduledFor}</li>
 * </ul>
 */
public record LeadRequest(

        // ------------------------------------------------ known before calling

        @NotBlank(message = "name is required")
        @Size(max = 150)
        @Schema(example = "Shrikant")
        String name,

        @NotBlank(message = "phone is required")
        @Schema(example = "+919876543210",
                description = "Spaces, dashes and brackets are fine; they are stripped before saving")
        @Pattern(regexp = "^[0-9+() -]{7,25}$",
                message = "phone must be 7-25 characters of digits, +, spaces, dashes or brackets")
        String phone,

        @Size(max = 200)
        @Schema(example = "My Home Sanctuary")
        String project,

        @Size(max = 100)
        @Schema(example = "website", description = "Where the enquiry came from")
        String source,

        @Size(max = 100)
        @Schema(example = "sanctuary-launch")
        String campaign,

        @Size(max = 64)
        String assignedTo,

        OffsetDateTime createdAt,

        // ------------------------------------------------ filled in after the call

        @Schema(description = "What was agreed on the call. Leave empty for a fresh lead.",
                example = "null")
        ActionType actionType,

        @Schema(description = "Status of that agreed action. Leave empty for a fresh lead.")
        LeadStatus status,

        @Size(max = 2000)
        @Schema(description = "What the lead asked about, usually captured during the call")
        String query,

        @Size(max = 2000)
        String notes,

        @Schema(description = "Required only when actionType is teamCallback")
        OffsetDateTime callbackAt,

        @Schema(description = "Required only when actionType is siteVisit or followUpCall")
        OffsetDateTime scheduledFor,

        OffsetDateTime reminderDueAt,

        Boolean reminderEnabled,

        Boolean confirmedByLead,

        @Pattern(regexp = "^[0-9+() -]{7,25}$",
                message = "callingPhone must be 7-25 characters of digits, +, spaces, dashes or brackets")
        String callingPhone,

        @Pattern(regexp = "^[0-9+() -]{7,25}$",
                message = "whatsappPhone must be 7-25 characters of digits, +, spaces, dashes or brackets")
        String whatsappPhone
) {
}
