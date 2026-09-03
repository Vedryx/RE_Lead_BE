package com.vedryxtech.voiceagent.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.call.persistence.LeadCallLogRepository;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The call ends and its outcome is reported while the audio is still finalising, so a
 * call log stops at "recording". This webhook is the only thing that ever moves it on.
 */
class LiveKitWebhookServiceTest {

    private static final String KEY = "recordings/my-home-sanctuary/2026/09/abc123/audio.ogg";

    private LeadCallLogRepository callLogs;
    private LiveKitWebhookService service;

    @BeforeEach
    void setUp() {
        callLogs = mock(LeadCallLogRepository.class);
        service = new LiveKitWebhookService(callLogs, mock(LeadRepository.class), new ObjectMapper());
    }

    @Test
    void a_finished_egress_makes_the_recording_available() {
        LeadCallLog callLog = callLog();
        when(callLogs.findByRecordingKey(KEY)).thenReturn(Optional.of(callLog));

        service.apply(egressEnded(KEY, 178_000_000_000L, 1_843_200L, null));

        assertThat(callLog.getRecordingStatus()).isEqualTo(RecordingStatus.AVAILABLE);
        assertThat(callLog.getRecordingDurationSeconds()).isEqualTo(178);
        assertThat(callLog.getRecordingSizeBytes()).isEqualTo(1_843_200L);
        assertThat(callLog.getRecordingReadyAt()).isNotNull();
        verify(callLogs).save(callLog);
    }

    @Test
    void a_failed_egress_ends_failed_rather_than_stuck_on_processing() {
        LeadCallLog callLog = callLog();
        when(callLogs.findByRecordingKey(KEY)).thenReturn(Optional.of(callLog));

        service.apply(egressEnded(KEY, 0, 0, "bucket does not exist"));

        assertThat(callLog.getRecordingStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(callLog.getRecordingReadyAt()).isNull();
    }

    @Test
    void a_negative_duration_falls_back_to_the_talk_time() {
        // LiveKit has been seen reporting a negative duration with no endedAt. A
        // negative length renders as a broken player rather than an obviously wrong
        // number, so the call's own measured talk time is used instead.
        LeadCallLog callLog = callLog();
        callLog.setTalkSeconds(185);
        when(callLogs.findByRecordingKey(KEY)).thenReturn(Optional.of(callLog));

        service.apply(egressEnded(KEY, -5_000_000_000L, 900L, null));

        assertThat(callLog.getRecordingDurationSeconds()).isEqualTo(185);
    }

    @Test
    void a_location_that_arrives_as_a_url_still_matches_the_stored_key() {
        LeadCallLog callLog = callLog();
        when(callLogs.findByRecordingKey(KEY)).thenReturn(Optional.of(callLog));

        String body = """
            {"event":"egress_ended","egressInfo":{"status":"EGRESS_COMPLETE","fileResults":[
              {"location":"https://acct.r2.cloudflarestorage.com/highrise-recordings/%s",
               "duration":120000000000,"size":500}]}}""".formatted(KEY);

        service.apply(body);

        assertThat(callLog.getRecordingStatus()).isEqualTo(RecordingStatus.AVAILABLE);
    }

    @Test
    void events_this_service_has_no_opinion_about_are_accepted_and_ignored() {
        // LiveKit retries anything that is not 2xx. Erroring on track_published would
        // earn a retry storm over an event with nothing to do.
        String result = service.apply("{\"event\":\"track_published\",\"track\":{}}");

        assertThat(result).contains("ignored");
        verify(callLogs, never()).save(any());
    }

    @Test
    void an_egress_for_a_call_we_do_not_have_is_not_an_error() {
        // Another environment sharing the bucket, or a call log since removed.
        when(callLogs.findByRecordingKey(anyString())).thenReturn(Optional.empty());

        assertThat(service.apply(egressEnded(KEY, 1_000_000_000L, 10L, null)))
                .contains("no call log");
    }

    @Test
    void a_body_that_is_not_json_does_not_throw() {
        assertThatCode(() -> service.apply("not json at all")).doesNotThrowAnyException();
    }

    private static LeadCallLog callLog() {
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId());
        callLog.setRecordingKey(KEY);
        callLog.setRecordingStatus(RecordingStatus.RECORDING);
        return callLog;
    }

    private static String egressEnded(String key, long durationNanos, long size, String error) {
        return """
            {"event":"egress_ended","egressInfo":{
              "egressId":"EG_test","status":"%s","error":"%s",
              "fileResults":[{"filename":"%s","duration":%d,"size":%d}]}}"""
                .formatted(error == null ? "EGRESS_COMPLETE" : "EGRESS_FAILED",
                        error == null ? "" : error, key, durationNanos, size);
    }
}
