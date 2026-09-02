package com.vedryxtech.voiceagent.call.api.dto;

import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Everything one call left behind, and how to open it.
 *
 * <p>{@code prefix} is where both artifacts live. It is not a URL and never will be —
 * S3-compatible storage has a flat key space, and a presigned URL signs exactly one
 * object. So the links below are minted per artifact, on this request, and expire.
 *
 * <p>Treat {@code audioUrl} and {@code transcriptUrl} as bearer tokens: whoever holds one
 * can fetch that object until it expires, with no further authentication. Do not log
 * them, store them, or send them on.
 */
@Schema(description = "Links to one call's recording and transcript, valid for a few minutes")
public record CallRecordingResponse(
        String callLogId,
        RecordingStatus recordingStatus,

        @Schema(description = "Where both artifacts live in object storage. Not openable.",
                example = "recordings/my-home-sanctuary/2026/09/6a97f01d4c69da009383ca53/")
        String prefix,

        @Schema(description = "Short-lived link to the audio; empty when there is none")
        String audioUrl,

        @Schema(description = "Short-lived link to the archived transcript JSON")
        String transcriptUrl,

        Integer durationSeconds,
        Integer sizeBytes,

        @Schema(description = "Turns of conversation captured, before any truncation")
        Integer transcriptTurnCount,

        @Schema(description = "True when the audio can be played right now")
        boolean playable,

        @Schema(description = "True when the words are readable, from Mongo or the archive")
        boolean hasTranscript,

        @Schema(description = "Kept for callers written against the old shape",
                deprecated = true)
        @Deprecated
        String recordingUrl
) {
}
