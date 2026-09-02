package com.vedryxtech.voiceagent.call.api;

import com.vedryxtech.voiceagent.call.api.dto.CallSessionResponse;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.call.application.LeadCallLogService;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.mapper.CallLogMapper;
import com.vedryxtech.voiceagent.common.error.ApiErrorFactory;
import com.vedryxtech.voiceagent.common.error.GlobalExceptionHandler;
import com.vedryxtech.voiceagent.exception.InvalidStateTransitionException;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.mapper.LeadMapper;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The "Call now" button.
 *
 * <p>The status code is the contract. This endpoint queues a call; the dialler places it
 * on its next pass. Answering 200 would tell the UI the phone is ringing when it is not,
 * and the spinner would lie to whoever pressed the button.
 */
@WebMvcTest(LeadCallController.class)
@Import({ApiErrorFactory.class, GlobalExceptionHandler.class,
         com.vedryxtech.voiceagent.config.WebConfig.class})
class LeadCallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CallOrchestrationService orchestrationService;
    @MockitoBean
    private LeadCallLogService callLogService;
    @MockitoBean
    private CallLogMapper callLogMapper;
    @MockitoBean
    private LeadMapper leadMapper;

    @Test
    @DisplayName("queuing a call answers 202, not 200")
    void startCallIsAccepted() throws Exception {
        Lead lead = new Lead();
        lead.setId(new ObjectId());
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId());
        callLog.setAttemptNumber(1);

        given(orchestrationService.startCall(anyString(), any()))
                .willReturn(new CallOrchestrationService.CallSession(lead, callLog, true));
        given(callLogMapper.toResponse(any(CallOrchestrationService.CallSession.class)))
                .willReturn(new CallSessionResponse(callLog.getId().toHexString(),
                        lead.getId().toHexString(), "+917972221220", "Dev", 1, true,
                        "recordings/p/2026/09/abc/audio.ogg", null));

        mockMvc.perform(post("/api/v1/leads/{id}/calls", new ObjectId().toHexString())
                        .with(jwt()).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.callLogId").exists());
    }

    @Test
    @DisplayName("a lead who asked not to be contacted is refused, by anyone")
    void doNotCallIsRefused() throws Exception {
        willThrow(new InvalidStateTransitionException("marked do_not_call"))
                .given(orchestrationService).startCall(anyString(), any());

        mockMvc.perform(post("/api/v1/leads/{id}/calls", new ObjectId().toHexString())
                        .with(jwt()).with(csrf())
                        .contentType("application/json").content("{}"))
                .andExpect(status().isConflict());
    }
}
