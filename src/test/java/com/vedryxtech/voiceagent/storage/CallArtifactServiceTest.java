package com.vedryxtech.voiceagent.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.domain.TranscriptTurn;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The archive half of a call's record. Every path here has to survive storage being
 * switched off or unreachable — the call happened either way.
 */
class CallArtifactServiceTest {

    private ObjectStorageProperties properties;
    private CallArtifactStore store;
    private CallArtifactService service;

    @BeforeEach
    void setUp() {
        properties = new ObjectStorageProperties();
        properties.setEnabled(true);
        properties.setPrefix("recordings");
        store = mock(CallArtifactStore.class);
        service = new CallArtifactService(properties, Optional.of(store), new ObjectMapper());
    }

    @Test
    void both_keys_are_decided_when_the_attempt_opens() {
        // Not at teardown: the agent needs the audio key in order to tell egress where
        // to write, and the CRM has to answer "is there a recording" before the webhook.
        LeadCallLog callLog = callLog();

        service.assignKeys(callLog, "My Home Sanctuary");

        assertThat(callLog.getRecordingPrefix())
                .isEqualTo("recordings/my-home-sanctuary/2026/09/6512a1b2c3d4e5f601020304/");
        assertThat(callLog.getRecordingKey()).endsWith("/audio.ogg");
        assertThat(callLog.getTranscriptKey()).endsWith("/transcript.json");
    }

    @Test
    void the_archived_transcript_can_be_read_without_the_database() {
        LeadCallLog callLog = callLog();
        service.assignKeys(callLog, "My Home Sanctuary");
        callLog.setTranscriptTurnCount(2);

        service.archiveTranscript(callLog, List.of(
                new TranscriptTurn("agent", "Namaste", 0),
                new TranscriptTurn("lead", "haan boliye", 4)));

        var key = org.mockito.ArgumentCaptor.forClass(String.class);
        var body = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).putJson(key.capture(), body.capture());

        assertThat(key.getValue()).endsWith("/transcript.json");
        assertThat(body.getValue())
                .contains("\"callLogId\"")
                .contains("\"project\"")
                .contains("\"turnCount\" : 2")
                .contains("\"redacted\" : true")
                .contains("haan boliye");
    }

    @Test
    void a_storage_failure_never_fails_the_outcome_that_produced_it() {
        // Mongo already has the turns. Throwing here would bounce a good outcome back
        // into the agent's outbox for a reason retrying cannot fix.
        LeadCallLog callLog = callLog();
        service.assignKeys(callLog, "P");
        doThrow(new RuntimeException("bucket unreachable")).when(store).putJson(anyString(), anyString());

        assertThatCode(() -> service.archiveTranscript(callLog, List.of(
                new TranscriptTurn("lead", "haan", 0)))).doesNotThrowAnyException();
    }

    @Test
    void nothing_is_written_for_a_call_that_said_nothing() {
        LeadCallLog callLog = callLog();
        service.assignKeys(callLog, "P");

        service.archiveTranscript(callLog, List.of());

        verify(store, never()).putJson(anyString(), anyString());
    }

    @Test
    void links_are_only_offered_for_files_that_are_really_there() {
        // An egress that failed leaves the transcript with no audio beside it, and a
        // lifecycle rule that has run leaves neither.
        LeadCallLog callLog = callLog();
        service.assignKeys(callLog, "P");
        when(store.list(anyString())).thenReturn(List.of(callLog.getTranscriptKey()));
        when(store.presignedGet(anyString())).thenReturn("https://signed");

        Map<String, String> links = service.linksFor(callLog);

        assertThat(links).containsOnlyKeys("transcriptUrl");
    }

    @Test
    void storage_switched_off_is_a_supported_deployment() {
        var off = new CallArtifactService(new ObjectStorageProperties(), Optional.empty(),
                new ObjectMapper());
        LeadCallLog callLog = callLog();

        assertThat(off.isEnabled()).isFalse();
        assertThat(off.linksFor(callLog)).isEmpty();
        assertThatCode(() -> off.archiveTranscript(callLog,
                List.of(new TranscriptTurn("lead", "haan", 0)))).doesNotThrowAnyException();
    }

    private static LeadCallLog callLog() {
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId("6512a1b2c3d4e5f601020304"));
        callLog.setCreatedAt(OffsetDateTime.of(2026, 9, 2, 12, 0, 0, 0, ZoneOffset.UTC));
        callLog.setProject("My Home Sanctuary");
        return callLog;
    }
}
