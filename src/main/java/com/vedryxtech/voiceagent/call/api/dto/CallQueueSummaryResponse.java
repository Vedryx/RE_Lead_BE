package com.vedryxtech.voiceagent.call.api.dto;

/** Current depth of the call queue. */
public record CallQueueSummaryResponse(
        long due
) {
}
