package com.vedryxtech.voiceagent.call.api;

import com.vedryxtech.voiceagent.call.api.dto.CallSessionResponse;
import com.vedryxtech.voiceagent.call.api.dto.CallLogResponse;
import com.vedryxtech.voiceagent.lead.api.dto.LeadResponse;
import com.vedryxtech.voiceagent.call.api.dto.RescheduleRequest;
import com.vedryxtech.voiceagent.call.api.dto.StartCallRequest;
import com.vedryxtech.voiceagent.call.mapper.CallLogMapper;
import com.vedryxtech.voiceagent.lead.mapper.LeadMapper;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.call.application.LeadCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "6. Lead Calls",
        description = "Lead-specific call actions and call history.")
@RestController
@RequestMapping(path = "/api/v1/leads/{leadId}", produces = "application/json")
public class LeadCallController {

    private final CallOrchestrationService orchestrationService;
    private final LeadCallLogService callLogService;
    private final CallLogMapper callLogMapper;
    private final LeadMapper leadMapper;

    public LeadCallController(CallOrchestrationService orchestrationService,
                              LeadCallLogService callLogService,
                              CallLogMapper callLogMapper,
                              LeadMapper leadMapper) {
        this.orchestrationService = orchestrationService;
        this.callLogService = callLogService;
        this.callLogMapper = callLogMapper;
        this.leadMapper = leadMapper;
    }

    @Operation(summary = "Call one specific lead now",
            description = "The Call now button. Ignores the schedule. Refused with 409 if the lead asked "
                    + "not to be contacted.")
    @PostMapping(path = "/calls", consumes = "application/json")
    public CallSessionResponse start(@PathVariable String leadId,
                                     @Valid @RequestBody(required = false) StartCallRequest request) {
        return callLogMapper.toResponse(orchestrationService.startCall(leadId, request));
    }

    @Operation(summary = "Every call ever made to one lead",
            description = "Newest first. This is the complete follow-up story: what we tried, when, "
                    + "and what they said each time.")
    @GetMapping("/calls")
    public List<CallLogResponse> history(@PathVariable String leadId) {
        return callLogService.historyForLead(leadId).stream()
                .map(callLogMapper::toResponse)
                .toList();
    }

    @Operation(summary = "Book a manual callback for a lead",
            description = "Use this when someone asks for another call outside an active call outcome.")
    @PostMapping(path = "/call-reschedules", consumes = "application/json")
    public LeadResponse reschedule(@PathVariable String leadId,
                                   @Valid @RequestBody RescheduleRequest request) {
        return leadMapper.toResponse(orchestrationService.reschedule(leadId, request));
    }
}
