package com.vedryxtech.voiceagent.webhook;

/**
 * What an {@code egress_ended} webhook tells us about one recording.
 *
 * @param recordingKey where the file was written — the key this service chose when the
 *                     attempt opened, which is how the event is matched to a call
 * @param durationSeconds length of the audio, or null when LiveKit reported none
 * @param sizeBytes size on disk
 * @param failed true when the egress ended in an error rather than a file
 * @param error what went wrong, when it did
 */
public record EgressResult(
        String recordingKey,
        Integer durationSeconds,
        Long sizeBytes,
        boolean failed,
        String error
) {
}
