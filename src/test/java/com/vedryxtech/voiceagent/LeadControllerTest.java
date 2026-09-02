package com.vedryxtech.voiceagent;

import com.vedryxtech.voiceagent.common.error.ApiErrorFactory;
import com.vedryxtech.voiceagent.common.error.GlobalExceptionHandler;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import com.vedryxtech.voiceagent.exception.DuplicateResourceException;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.lead.mapper.LeadMapperImpl;
import com.vedryxtech.voiceagent.mapper.MapperSupport;
import com.vedryxtech.voiceagent.lead.application.LeadService;
import com.vedryxtech.voiceagent.lead.api.LeadController;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import com.vedryxtech.voiceagent.lead.application.LeadAuditService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LeadController.class)
@Import({
        LeadMapperImpl.class,
        MapperSupport.class,
        ApiErrorFactory.class,
        GlobalExceptionHandler.class,
        com.vedryxtech.voiceagent.config.WebConfig.class
})
class LeadControllerTest {

    /** A fresh lead is the normal case: a name and a number, nothing else. */
    private static final String FRESH_LEAD_BODY = """
            {
              "name": "Shrikant",
              "phone": "+919876543210",
              "project": "My Home Sanctuary"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeadService service;

    @MockitoBean
    private LeadAuditService audit;

    @Test
    void createsAFreshLeadFromJustANameAndNumber() throws Exception {
        given(service.create(any(LeadRequest.class))).willReturn(freshLead());

        mockMvc.perform(post("/api/v1/leads")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRESH_LEAD_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("6512a1b2c3d4e5f601020304"))
                .andExpect(jsonPath("$.name").value("Shrikant"))
                .andExpect(jsonPath("$.callingPhone").value("+919876543210"))
                // Queued to be called, with no action agreed yet.
                .andExpect(jsonPath("$.pipelineStatus").value("new"))
                .andExpect(jsonPath("$.actionType").value(nullValue()))
                .andExpect(jsonPath("$.status").value(nullValue()))
                .andExpect(jsonPath("$.callbackAt").value(nullValue()));
    }

    @Test
    void nameAndPhoneAreTheOnlyRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/leads")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"project\": \"My Home Sanctuary\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.project").doesNotExist())
                .andExpect(jsonPath("$.fieldErrors.actionType").doesNotExist());
    }

    @Test
    void duplicatePhoneNumberReturns409() throws Exception {
        given(service.create(any(LeadRequest.class)))
                .willThrow(DuplicateResourceException.callingPhone("+919876543210"));

        mockMvc.perform(post("/api/v1/leads")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRESH_LEAD_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void upsertsALeadWithPutOnTheCollection() throws Exception {
        given(service.upsert(any(LeadRequest.class)))
                .willReturn(new LeadService.UpsertResult(freshLead(), false));

        mockMvc.perform(put("/api/v1/leads")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(FRESH_LEAD_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("6512a1b2c3d4e5f601020304"))
                .andExpect(jsonPath("$.callingPhone").value("+919876543210"));
    }

    @Test
    void unknownLeadReturns404() throws Exception {
        given(service.getById(anyString())).willThrow(ResourceNotFoundException.lead("id", "nope"));

        mockMvc.perform(get("/api/v1/leads/nope").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private Lead freshLead() {
        Lead lead = new Lead();
        lead.setId(new ObjectId("6512a1b2c3d4e5f601020304"));
        lead.setCreatedAt(OffsetDateTime.parse("2026-08-26T19:52:06.409413Z"));
        lead.setName("Shrikant");
        lead.setPhone("+919876543210");
        lead.setCallingPhone("+919876543210");
        lead.setProject("My Home Sanctuary");
        lead.setPipelineStatus(LeadPipelineStatus.NEW);
        lead.setNextAttemptAt(OffsetDateTime.parse("2026-08-26T19:52:06.409413Z"));
        return lead;
    }
}
