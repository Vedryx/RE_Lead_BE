package com.vedryxtech.voiceagent.call.api.dto;

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
        boolean recordingEnabled
) {
}
