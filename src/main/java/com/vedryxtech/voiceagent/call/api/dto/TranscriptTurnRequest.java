package com.vedryxtech.voiceagent.call.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** One turn of the conversation, as the agent reports it. */
@Schema(description = "One thing said on the call")
public record TranscriptTurnRequest(

        @NotBlank
        @Pattern(regexp = "agent|lead", message = "role must be 'agent' or 'lead'")
        @Schema(example = "lead")
        String role,

        @NotBlank
        @Size(max = 4000)
        @Schema(example = "Haan, 2 BHK dekh raha hoon")
        String text,

        @Min(0)
        @Schema(description = "Seconds from the first turn of the call", example = "14")
        Integer atSeconds
) {
}
