package com.vedryxtech.voiceagent.call.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** "Call me on Thursday at 6" - captured without burning a retry attempt. */
public record RescheduleRequest(

        @NotNull(message = "requested_at is required")
        @Schema(example = "2026-09-03T18:30:00+05:30", description = "A time outside calling hours is moved to the next morning")
        OffsetDateTime requestedAt,

        @Size(max = 2000)
        @Schema(example = "Lead was driving, asked for Thursday evening.")
        String notes
) {
}
