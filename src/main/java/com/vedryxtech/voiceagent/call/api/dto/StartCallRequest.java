package com.vedryxtech.voiceagent.call.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Opens a call attempt against a lead. */
public record StartCallRequest(

        /** Repeat-safe: the same key returns the attempt already opened instead of a second one. */
        @Size(max = 128)
        @Schema(example = "worker-1-attempt-abc123")
        String idempotencyKey,

        /** {@code ai_agent} by default, or a user id when a human dials. */
        @Size(max = 64)
        @Schema(example = "ai_agent")
        String handledBy,

        /** Overrides the organization policy for this one call. */
        Boolean recordingEnabled
) {
}
