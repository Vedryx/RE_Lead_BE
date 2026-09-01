package com.vedryxtech.voiceagent.lead.api;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import com.vedryxtech.voiceagent.lead.api.dto.LeadPatchRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadResponse;
import com.vedryxtech.voiceagent.common.pagination.PageResponse;
import com.vedryxtech.voiceagent.lead.mapper.LeadMapper;
import com.vedryxtech.voiceagent.lead.application.LeadSearchCriteria;
import com.vedryxtech.voiceagent.lead.application.LeadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;

/** CRUD over the {@code lead} collection, scoped to the organization on the access token. */
@Tag(name = "5. Leads",
        description = "The people to call. One record per phone number, per company.")
@RestController
@RequestMapping(path = "/api/v1/leads", produces = "application/json")
public class LeadController {

    private final LeadService service;
    private final LeadMapper mapper;

    public LeadController(LeadService service, LeadMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ CREATE

    @Operation(summary = "Add a lead to be called",
            description = "A fresh lead needs only a name and a phone number. It is saved with status "
                    + "'new' and queued for a call straight away; what was agreed, when to visit and "
                    + "when to call back are filled in later by Report what happened on the call. "
                    + "Fails with 409 if this phone number is already a lead.")
    @PostMapping(consumes = "application/json")
    public ResponseEntity<LeadResponse> create(@Valid @RequestBody LeadRequest request) {
        Lead lead = service.create(request);
        return ResponseEntity.created(location(lead)).body(mapper.toResponse(lead));
    }

    @Operation(summary = "Add a lead, or update it if the number already exists",
            description = "Safe to call repeatedly. This is what the voice agent should use, "
                    + "because the same person calls back.")
    @PutMapping(consumes = "application/json")
    public ResponseEntity<LeadResponse> upsert(@Valid @RequestBody LeadRequest request) {
        LeadService.UpsertResult result = service.upsert(request);
        LeadResponse body = mapper.toResponse(result.lead());
        return result.created()
                ? ResponseEntity.created(location(result.lead())).body(body)
                : ResponseEntity.ok(body);
    }

    // -------------------------------------------------------------------- READ

    @Operation(summary = "Search leads",
            description = "Every filter is optional. Common ones: pipelineStatus=queued for work still to do, "
                    + "finalStatus=siteVisitBooked for wins, hasRecording=true for calls you can listen to.")
    @GetMapping
    public PageResponse<LeadResponse> list(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) ActionType actionType,
            @RequestParam(required = false) LeadStatus status,
            @RequestParam(required = false) LeadPipelineStatus pipelineStatus,
            @RequestParam(required = false) LeadFinalStatus finalStatus,
            @RequestParam(required = false) CallDisposition disposition,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) Boolean confirmedByLead,
            @RequestParam(required = false) Boolean hasRecording,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime createdTo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime scheduledFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime scheduledTo,
            @ParameterObject
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        LeadSearchCriteria criteria = new LeadSearchCriteria(
                project, actionType, status, pipelineStatus, finalStatus, disposition,
                phone, name, assignedTo, confirmedByLead, hasRecording,
                createdFrom, createdTo, scheduledFrom, scheduledTo);

        Page<Lead> results = service.search(criteria, pageable);
        return PageResponse.from(results, mapper::toResponse);
    }

    @Operation(summary = "Get one lead",
            description = "By the id returned when it was created. That id is the only identifier a lead has.")
    @GetMapping("/{id}")
    public LeadResponse getById(@PathVariable String id) {
        return mapper.toResponse(service.getById(id));
    }

    // ------------------------------------------------------------------ UPDATE

    @Operation(summary = "Replace a lead",
            description = "Anything you leave out is cleared. Use Update-some-fields if you only want to change a few.")
    @PutMapping(path = "/{id}", consumes = "application/json")
    public LeadResponse replace(@PathVariable String id, @Valid @RequestBody LeadRequest request) {
        return mapper.toResponse(service.replace(id, request));
    }

    @Operation(summary = "Update some fields of a lead",
            description = "Send only what changes. Setting doNotCall to true stops all future calls immediately.")
    @PatchMapping(path = "/{id}", consumes = "application/json")
    public LeadResponse patch(@PathVariable String id, @Valid @RequestBody LeadPatchRequest request) {
        return mapper.toResponse(service.patch(id, request));
    }

    private URI location(Lead lead) {
        return UriComponentsBuilder.fromPath("/api/v1/leads/{id}")
                .buildAndExpand(lead.getIdAsString())
                .toUri();
    }
}
