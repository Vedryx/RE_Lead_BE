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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    private LeadCallLogService callLogService;

    @MockitoBean
    private com.vedryxtech.voiceagent.call.mapper.CallLogMapper callLogMapper;

    @Test
    void recordingResponseUsesCamelCaseFields() throws Exception {
        given(callLogService.require(anyString())).willReturn(playableCallLog());

        mockMvc.perform(get("/api/v1/calls/6512a1b2c3d4e5f601020304/recording").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callLogId").value("6512a1b2c3d4e5f601020304"))
                .andExpect(jsonPath("$.recordingStatus").value("available"))
                .andExpect(jsonPath("$.recordingUrl").value("https://recordings.local/call-abc.mp3"))
                .andExpect(jsonPath("$.durationSeconds").value(214))
                .andExpect(jsonPath("$.playable").value(true));
    }

    private LeadCallLog playableCallLog() {
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId("6512a1b2c3d4e5f601020304"));
        callLog.setRecordingStatus(RecordingStatus.AVAILABLE);
        callLog.setRecordingUrl("https://recordings.local/call-abc.mp3");
        callLog.setRecordingDurationSeconds(214);
        return callLog;
    }
}
