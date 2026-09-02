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

        @Schema(description = "What is already known about this lead. Absent on a first call.")
        CallContext context
) {
}
