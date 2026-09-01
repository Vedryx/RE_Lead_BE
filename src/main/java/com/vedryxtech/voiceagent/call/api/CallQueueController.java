package com.vedryxtech.voiceagent.call.api;

import com.vedryxtech.voiceagent.call.api.dto.CallQueueSummaryResponse;
import com.vedryxtech.voiceagent.call.api.dto.CallSessionResponse;
import com.vedryxtech.voiceagent.call.api.dto.ReleasedCallsResponse;
import com.vedryxtech.voiceagent.call.mapper.CallLogMapper;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "8. Call Queue",
        description = "Dialler queue operations, separated from call log resources.")
@RestController
@RequestMapping(path = "/api/v1/call-queue", produces = "application/json")
public class CallQueueController {

    private final CallOrchestrationService orchestrationService;
    private final CallLogMapper callLogMapper;

    public CallQueueController(CallOrchestrationService orchestrationService,
                               CallLogMapper callLogMapper) {
        this.orchestrationService = orchestrationService;
        this.callLogMapper = callLogMapper;
    }

    @Operation(summary = "How many leads are due right now")
    @GetMapping
    public CallQueueSummaryResponse summary() {
        return new CallQueueSummaryResponse(orchestrationService.dueCount());
    }

    @Operation(summary = "Take work to call",
            description = "Hands the caller a batch of leads that are due right now and marks them as being called, "
                    + "so two callers never get the same person. Each item includes the room to call in.")
    @PostMapping("/claims")
    public List<CallSessionResponse> claim(@RequestParam(defaultValue = "10") int limit) {
        return orchestrationService.claimNext(limit).stream()
                .map(callLogMapper::toResponse)
                .toList();
    }

    @Operation(summary = "Release stuck calls back to the queue",
            description = "If a caller crashes mid-call its lead would sit unreachable forever. "
                    + "This puts those back in the queue.")
    @PostMapping("/recoveries")
    public ReleasedCallsResponse releaseStuck() {
        return new ReleasedCallsResponse(orchestrationService.releaseStuckAttempts());
    }
}
