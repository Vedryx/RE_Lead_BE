package com.vedryxtech.voiceagent.call.api;

import com.vedryxtech.voiceagent.call.api.dto.CallLogResponse;
import com.vedryxtech.voiceagent.call.api.dto.CallOutcomeRequest;
import com.vedryxtech.voiceagent.call.api.dto.CallRecordingResponse;
import com.vedryxtech.voiceagent.storage.CallArtifactService;
import com.vedryxtech.voiceagent.common.pagination.PageResponse;
import com.vedryxtech.voiceagent.call.mapper.CallLogMapper;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.call.application.LeadCallLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Call records, outcomes, and recordings.
 *
 * <p>The dialler claims work through the call queue endpoints, then reports outcomes here when
 * the leg hangs up.</p>
 */
@Tag(name = "7. Calls",
        description = "Call records, outcomes, searchable history, and recordings.")
@RestController
@RequestMapping(path = "/api/v1/calls", produces = "application/json")
public class CallController {

    private final CallOrchestrationService orchestrationService;
    private final LeadCallLogService callLogService;
    private final CallLogMapper callLogMapper;
    private final CallArtifactService artifacts;

    public CallController(CallOrchestrationService orchestrationService,
                          LeadCallLogService callLogService,
                          CallLogMapper callLogMapper,
                          CallArtifactService artifacts) {
        this.orchestrationService = orchestrationService;
        this.callLogService = callLogService;
        this.callLogMapper = callLogMapper;
        this.artifacts = artifacts;
    }

    @Operation(summary = "Report what happened on the call",
            description = "Call this once the call ends. It decides everything that happens next: retry later, "
                    + "book the callback they asked for, or close the lead. "
                    + "outcome says whether the phone connected; disposition says what the person agreed to.")
    @PostMapping(path = "/{callLogId}/outcome", consumes = "application/json")
    public CallLogResponse recordOutcome(@PathVariable String callLogId,
                                         @Valid @RequestBody CallOutcomeRequest request) {
        return callLogMapper.toResponse(orchestrationService.recordOutcome(callLogId, request));
    }

    @Operation(summary = "Get one call, with its full minute-by-minute timeline")
    @GetMapping("/{callLogId}")
    public CallLogResponse getCallLog(@PathVariable String callLogId) {
        return callLogMapper.toResponse(callLogService.require(callLogId));
    }

    @Operation(summary = "Search all calls",
            description = "Filter by outcome (answered, no_answer, busy...), by what was agreed, "
                    + "by date, or by whether a recording exists.")
    @GetMapping
    public PageResponse<CallLogResponse> list(
            @RequestParam(required = false) String leadId,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String outcome,
            @RequestParam(required = false) String disposition,
            @RequestParam(required = false) String recordingStatus,
            @RequestParam(required = false) Boolean hasRecording,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        LeadCallLogService.CallLogSearchCriteria criteria = new LeadCallLogService.CallLogSearchCriteria(
                leadId, phone, outcome, disposition, recordingStatus, hasRecording, from, to);

        return PageResponse.from(callLogService.search(criteria, pageable),
                callLogMapper::toResponse);
    }

    // -------------------------------------------------------------- recordings

    @Operation(summary = "All calls you can listen to",
            description = "Newest first. Each item has a recordingUrl you can open in any audio player.")
    @GetMapping("/recordings")
    public PageResponse<CallLogResponse> recordings(
            @ParameterObject
            @PageableDefault(size = 20, sort = "recordingReadyAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return PageResponse.from(
                callLogService.recordings(pageable),
                callLogMapper::toResponse);
    }

    @Operation(summary = "Get the recording and transcript for one call",
            description = "Returns the storage prefix both artifacts share, plus a short-lived "
                    + "link to each. The links are bearer tokens: anyone holding one can open "
                    + "that file until it expires, so do not pass them on. playable=false means "
                    + "the audio is still processing, was never recorded, or has aged out.")
    @GetMapping("/{callLogId}/recording")
    public CallRecordingResponse recording(@PathVariable String callLogId) {
        var callLog = callLogService.require(callLogId);
        var links = artifacts.linksFor(callLog);

        // Only hand out audio once the recording is actually finished. A link to a file
        // still being written plays as silence, which reads as a broken call rather than
        // a recording that is thirty seconds away.
        boolean finished = callLog.getRecordingStatus() != null
                && callLog.getRecordingStatus().isPlayable();
        String audioUrl = finished ? links.getOrDefault("audioUrl", "") : "";
        String transcriptUrl = links.getOrDefault("transcriptUrl", "");

        boolean hasTranscript = !transcriptUrl.isEmpty()
                || (callLog.getTranscript() != null && !callLog.getTranscript().isEmpty());

        return new CallRecordingResponse(
                callLog.getIdAsString(),
                callLog.getRecordingStatus(),
                callLog.getRecordingPrefix(),
                audioUrl,
                transcriptUrl,
                callLog.getRecordingDurationSeconds(),
                callLog.getRecordingSizeBytes() == null ? null
                        : Math.toIntExact(callLog.getRecordingSizeBytes()),
                callLog.getTranscriptTurnCount(),
                !audioUrl.isEmpty(),
                hasTranscript,
                audioUrl);
    }
}
