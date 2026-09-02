package com.vedryxtech.voiceagent.call.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The open attempt handed back to the caller. Everything needed to place the call and, later,
 * to report what happened against the right attempt.
 */
public record CallSessionResponse(
        String callLogId,
        String leadId,
        String phone,
        String name,
        int attemptNumber,
        boolean recordingEnabled,

        @Schema(description = "Exactly where the audio must be written. The agent passes this "
                + "to egress rather than letting it invent a name, so the CRM knows the "
                + "location before the phone rings.",
                example = "recordings/my-home-sanctuary/2026/09/6a97f01d.../audio.ogg")
        String recordingKey,

        @Schema(description = "What is already known about this lead. Absent on a first call.")
        CallContext context
) {
}
