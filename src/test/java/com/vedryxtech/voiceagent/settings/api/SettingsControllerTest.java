package com.vedryxtech.voiceagent.settings.api;

import com.vedryxtech.voiceagent.common.error.ApiErrorFactory;
import com.vedryxtech.voiceagent.common.error.GlobalExceptionHandler;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The frozen agent contract: {@code GET /api/v1/organizations/current} must keep returning a
 * {@code callPolicy} key with the exact CallPolicy field names highrise reads at start-up
 * ({@code highrise/agent/backend.py:105-113}). Renaming, nesting or omitting it here silently
 * breaks the voice agent.
 */
@WebMvcTest(SettingsController.class)
@Import({ApiErrorFactory.class, GlobalExceptionHandler.class,
        com.vedryxtech.voiceagent.config.WebConfig.class})
class SettingsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @Test
    void getReturnsCallPolicyAtTheTopLevelForHighrise() throws Exception {
        AppSettings settings = new AppSettings();
        settings.setTimezone("Asia/Kolkata");
        settings.setApiKeyPrefix("vdx_bed0f54a");
        settings.setApiKeyCreatedAt(OffsetDateTime.parse("2026-09-01T00:00:00Z"));
        CallPolicy policy = CallPolicy.defaults();
        settings.setCallPolicy(policy);
        given(settingsService.current()).willReturn(settings);

        mockMvc.perform(get("/api/v1/organizations/current").with(jwt()))
                .andExpect(status().isOk())
                // Frozen keys — highrise reads these verbatim.
                .andExpect(jsonPath("$.callPolicy.maxAttempts").value(4))
                .andExpect(jsonPath("$.callPolicy.maxAttemptsPerDay").value(2))
                .andExpect(jsonPath("$.callPolicy.callingWindowStart").value("09:00"))
                .andExpect(jsonPath("$.callPolicy.callingWindowEnd").value("20:00"))
                .andExpect(jsonPath("$.callPolicy.visitingHoursStart").value("10:00"))
                .andExpect(jsonPath("$.callPolicy.bookingHorizonDays").value(30))
                .andExpect(jsonPath("$.callPolicy.retryBackoffMinutes.noAnswer").value(120))
                .andExpect(jsonPath("$.timezone").value("Asia/Kolkata"))
                .andExpect(jsonPath("$.apiKeyPrefix").value("vdx_bed0f54a"));
    }

    @Test
    void putValidationSurfacesFieldErrors422() throws Exception {
        CallPolicy stored = CallPolicy.defaults();
        given(settingsService.currentPolicy()).willReturn(stored);

        mockMvc.perform(put("/api/v1/organizations/current/call-policy")
                        .with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAttempts\":-1,\"callingWindowStart\":\"25:99\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.fieldErrors.maxAttempts").exists())
                .andExpect(jsonPath("$.fieldErrors.callingWindowStart").exists());
    }

    @Test
    void putPartialPatchMergesOntoStoredPolicy() throws Exception {
        CallPolicy stored = CallPolicy.defaults();
        stored.setCallingWindowStart("07:30");
        stored.setCallingWindowEnd("21:30");
        given(settingsService.currentPolicy()).willReturn(stored);

        // Return whatever the merger produced.
        given(settingsService.updateCallPolicy(any())).willAnswer(inv -> {
            AppSettings out = new AppSettings();
            out.setCallPolicy(inv.getArgument(0));
            return out;
        });

        mockMvc.perform(put("/api/v1/organizations/current/call-policy")
                        .with(jwt()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxAttempts\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callPolicy.maxAttempts").value(6))
                // Omitted fields must survive.
                .andExpect(jsonPath("$.callPolicy.callingWindowStart").value("07:30"))
                .andExpect(jsonPath("$.callPolicy.callingWindowEnd").value("21:30"));
    }
}
