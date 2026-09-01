package com.vedryxtech.voiceagent.call.api.dto;

import com.vedryxtech.voiceagent.call.domain.RecordingStatus;

/** Playback metadata for a single call recording. */
public record CallRecordingResponse(
        String callLogId,
        RecordingStatus recordingStatus,
        String recordingUrl,
        Integer durationSeconds,
        boolean playable
) {
}
