package com.vedryxtech.voiceagent;

import com.vedryxtech.voiceagent.common.error.ApiErrorFactory;
import com.vedryxtech.voiceagent.common.error.GlobalExceptionHandler;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.call.application.LeadCallLogService;
import com.vedryxtech.voiceagent.call.api.CallController;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.vedryxtech.voiceagent.storage.CallArtifactService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.vedryxtech.voiceagent.call.domain.TranscriptTurn;
import java.util.List;
import java.util.Map;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CallController.class)
@Import({ApiErrorFactory.class, GlobalExceptionHandler.class, com.vedryxtech.voiceagent.config.WebConfig.class})
class CallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CallOrchestrationService orchestrationService;

    @MockitoBean
    private CallArtifactService artifacts;

    @MockitoBean
    private LeadCallLogService callLogService;

    @MockitoBean
    private com.vedryxtech.voiceagent.call.mapper.CallLogMapper callLogMapper;

    @Test
    void recordingResponseServesTheAgentPostedUrlWhenPresent() throws Exception {
        // M-8: when the agent posts a recording_url on the outcome, that is the URL the
        // README documents as "listed as playable" — round-trip it. We still hand out a
        // presigned transcript link separately (that is our own R2 write).
        given(callLogService.require(anyString())).willReturn(playableCallLog());
        given(artifacts.linksFor(any())).willReturn(Map.of(
                "audioUrl", "https://r2.example/audio.ogg?X-Amz-Signature=abc",
                "transcriptUrl", "https://r2.example/transcript.json?X-Amz-Signature=def"));

        mockMvc.perform(get("/api/v1/calls/6512a1b2c3d4e5f601020304/recording").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callLogId").value("6512a1b2c3d4e5f601020304"))
                .andExpect(jsonPath("$.recordingStatus").value("available"))
                .andExpect(jsonPath("$.prefix").value("recordings/mhs/2026/09/6512a1b2c3d4e5f601020304/"))
                // The URL the agent posted round-trips; it is the primary "playable" contract.
                .andExpect(jsonPath("$.audioUrl").value("https://recordings.local/call-abc.mp3"))
                .andExpect(jsonPath("$.transcriptUrl").value("https://r2.example/transcript.json?X-Amz-Signature=def"))
                .andExpect(jsonPath("$.durationSeconds").value(214))
                .andExpect(jsonPath("$.playable").value(true))
                .andExpect(jsonPath("$.hasTranscript").value(true));
    }

    @Test
    void recordingResponseFallsBackToR2WhenNoStoredUrl() throws Exception {
        // If the agent did not post recording_url (LiveKit egress wrote straight into R2),
        // fall back to a freshly presigned link to the audio_key the CRM chose.
        LeadCallLog callLog = playableCallLog();
        callLog.setRecordingUrl(null);
        given(callLogService.require(anyString())).willReturn(callLog);
        given(artifacts.linksFor(any())).willReturn(Map.of(
                "audioUrl", "https://r2.example/audio.ogg?X-Amz-Signature=abc",
                "transcriptUrl", "https://r2.example/transcript.json?X-Amz-Signature=def"));

        mockMvc.perform(get("/api/v1/calls/6512a1b2c3d4e5f601020304/recording").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioUrl").value("https://r2.example/audio.ogg?X-Amz-Signature=abc"))
                .andExpect(jsonPath("$.playable").value(true));
    }

    @Test
    void audioStillProcessingIsNotHandedOut() throws Exception {
        // A link to a file still being written plays as silence, which reads as a broken
        // call rather than a recording that is thirty seconds away.
        LeadCallLog callLog = playableCallLog();
        callLog.setRecordingStatus(RecordingStatus.PROCESSING);
        given(callLogService.require(anyString())).willReturn(callLog);
        given(artifacts.linksFor(any())).willReturn(Map.of(
                "transcriptUrl", "https://r2.example/transcript.json?X-Amz-Signature=def"));

        mockMvc.perform(get("/api/v1/calls/6512a1b2c3d4e5f601020304/recording").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.audioUrl").value(""))
                .andExpect(jsonPath("$.playable").value(false))
                .andExpect(jsonPath("$.transcriptUrl").value("https://r2.example/transcript.json?X-Amz-Signature=def"))
                .andExpect(jsonPath("$.hasTranscript").value(true));
    }

    @Test
    void a_transcript_in_mongo_counts_even_with_no_bucket_configured() throws Exception {
        // Storage off is a supported deployment: the words are still readable.
        // Clear the recording_url to prove hasTranscript stays true without any audio path.
        LeadCallLog callLog = playableCallLog();
        callLog.setRecordingUrl(null);
        callLog.setTranscript(List.of(new TranscriptTurn("lead", "haan boliye", 4)));
        given(callLogService.require(anyString())).willReturn(callLog);
        given(artifacts.linksFor(any())).willReturn(Map.of());

        mockMvc.perform(get("/api/v1/calls/6512a1b2c3d4e5f601020304/recording").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasTranscript").value(true))
                .andExpect(jsonPath("$.audioUrl").value(""));
    }

    private LeadCallLog playableCallLog() {
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId("6512a1b2c3d4e5f601020304"));
        callLog.setRecordingStatus(RecordingStatus.AVAILABLE);
        callLog.setRecordingUrl("https://recordings.local/call-abc.mp3");
        callLog.setRecordingDurationSeconds(214);
        callLog.setRecordingPrefix("recordings/mhs/2026/09/6512a1b2c3d4e5f601020304/");
        callLog.setRecordingKey("recordings/mhs/2026/09/6512a1b2c3d4e5f601020304/audio.ogg");
        callLog.setTranscriptKey("recordings/mhs/2026/09/6512a1b2c3d4e5f601020304/transcript.json");
        return callLog;
    }
}
