package com.vedryxtech.voiceagent.call.api.dto;

/** Number of stuck calls released back into the queue. */
public record ReleasedCallsResponse(
        long released
) {
}
