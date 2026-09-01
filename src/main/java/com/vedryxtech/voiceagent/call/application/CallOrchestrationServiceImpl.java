package com.vedryxtech.voiceagent.call.application;

import com.vedryxtech.voiceagent.config.CallPolicyProperties;
import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallEvent;
import com.vedryxtech.voiceagent.call.domain.CallEventType;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.organization.domain.CallPolicy;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.call.api.dto.CallOutcomeRequest;
import com.vedryxtech.voiceagent.call.api.dto.RescheduleRequest;
import com.vedryxtech.voiceagent.call.api.dto.StartCallRequest;
import com.vedryxtech.voiceagent.exception.InvalidLeadPayloadException;
import com.vedryxtech.voiceagent.exception.InvalidStateTransitionException;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.call.persistence.LeadCallLogRepository;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import com.vedryxtech.voiceagent.security.CurrentActor;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.organization.application.OrganizationService;
import com.vedryxtech.voiceagent.common.util.PhoneNumbers;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Owns every write to {@code pipeline_status}, {@code final_status} and {@code leads_log}.
 * CRUD never moves a lead through the pipeline; only this service does, so the history in
 * {@code leads_log} is guaranteed to explain the state on the lead.
 */
@Service
public class CallOrchestrationServiceImpl implements CallOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(CallOrchestrationServiceImpl.class);

    private static final String AI_AGENT = "ai_agent";
    private static final String SYSTEM_DIRECTION = "system";

    /** Statuses the dialler is allowed to pick up. */
    private static final List<String> CLAIMABLE = List.of(
            LeadPipelineStatus.NEW.getValue(),
            LeadPipelineStatus.QUEUED.getValue(),
            LeadPipelineStatus.RETRY_SCHEDULED.getValue(),
            LeadPipelineStatus.CALLBACK_SCHEDULED.getValue());

    private final LeadRepository leadRepository;
    private final LeadCallLogRepository callLogRepository;
    private final OrganizationService organizationService;
    private final MongoTemplate mongoTemplate;
    private final CallPolicyProperties dialerProperties;
    private final CurrentActor currentActor;

    public CallOrchestrationServiceImpl(LeadRepository leadRepository,
                                        LeadCallLogRepository callLogRepository,
                                        OrganizationService organizationService,
                                        MongoTemplate mongoTemplate,
                                        CallPolicyProperties dialerProperties,
                                        CurrentActor currentActor) {
        this.leadRepository = leadRepository;
        this.callLogRepository = callLogRepository;
        this.organizationService = organizationService;
        this.mongoTemplate = mongoTemplate;
        this.dialerProperties = dialerProperties;
        this.currentActor = currentActor;
    }

    // ------------------------------------------------------------------ claim

    @Override
    public List<CallSession> claimNext(int limit) {
        Organization organization = organizationService.current();
        CallPolicy policy = policyOf(organization);

        int batch = Math.min(Math.max(limit, 1), dialerProperties.getBatchSize());
        List<CallSession> sessions = new ArrayList<>(batch);

        for (int i = 0; i < batch; i++) {
            Lead claimed = claimOne();
            if (claimed == null) {
                break;
            }
            if (attemptsToday(claimed, organization) >= policy.maxAttemptsPerDayOrDefault()) {
                // Over the daily cap: put it back with tomorrow's window instead of dialling.
                deferToNextWindow(claimed, organization, policy);
                continue;
            }
            sessions.add(openAttempt(claimed, organization, policy, null, AI_AGENT, null));
        }

        log.debug("Claimed {} lead(s)", sessions.size());
        return sessions;
    }

    /**
     * One atomic claim. {@code findAndModify} flips exactly one due lead to {@code dialing},
     * so two workers polling concurrently can never take the same lead.
     */
    private Lead claimOne() {
        Date now = Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant());

        Query query = Query.query(Criteria.where("do_not_call").ne(Boolean.TRUE)
                        .and("pipeline_status").in(CLAIMABLE)
                        .and("next_attempt_at").lte(now))
                .with(Sort.by(Sort.Direction.ASC, "next_attempt_at"));

        Update update = new Update()
                .set("pipeline_status", LeadPipelineStatus.DIALING.getValue())
                .set("updated_at", now);

        return mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Lead.class);
    }

    // ------------------------------------------------------------------- start

    @Override
    public CallSession startCall(String leadId, StartCallRequest request) {
        Organization organization = organizationService.current();
        CallPolicy policy = policyOf(organization);

        Lead lead = requireLead(leadId);

        if (lead.isDoNotCall()) {
            throw new InvalidStateTransitionException(
                    "Lead " + lead.getIdAsString() + " is marked do_not_call and must not be dialled");
        }
        if (lead.getPipelineStatus() != null && lead.getPipelineStatus().isActive()) {
            // Already dialling: hand back the open attempt rather than opening a second one.
            Optional<LeadCallLog> open = callLogRepository
                    .findFirstByLeadIdOrderByAttemptNumberDesc(lead.getId())
                    .filter(existing -> !existing.isClosed());
            if (open.isPresent()) {
                return toSession(lead, open.get(), policy, request);
            }
        }

        String idempotencyKey = request == null ? null : request.idempotencyKey();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<LeadCallLog> existing = callLogRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return toSession(lead, existing.get(), policy, request);
            }
        }

        lead.setPipelineStatus(LeadPipelineStatus.DIALING);
        lead.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lead = leadRepository.save(lead);

        String handledBy = request != null && request.handledBy() != null
                ? request.handledBy()
                : currentActor.actor();

        return openAttempt(lead, organization, policy, idempotencyKey, handledBy,
                request == null ? null : request.recordingEnabled());
    }

    /** Creates the {@code leads_log} row for one attempt. */
    private CallSession openAttempt(Lead lead, Organization organization, CallPolicy policy,
                                    String idempotencyKey, String handledBy, Boolean recordingOverride) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int attemptNumber = lead.attemptCountOrZero() + 1;
        boolean recordingEnabled = recordingOverride != null
                ? recordingOverride
                : policy.recordingEnabledOrDefault();

        LeadCallLog callLog = new LeadCallLog();
        callLog.setLeadId(lead.getId());
        callLog.setPhone(lead.getCallingPhone());
        callLog.setName(lead.getName());
        callLog.setProject(lead.getProject());
        callLog.setAttemptNumber(attemptNumber);
        callLog.setIdempotencyKey(idempotencyKey);
        callLog.setHandledBy(handledBy);
        callLog.setDirection("outbound");
        callLog.setPipelineStatusBefore(lead.getPipelineStatus());
        callLog.setQueuedAt(lead.getNextAttemptAt() != null ? lead.getNextAttemptAt() : now);
        callLog.setDialStartedAt(now);
        callLog.setCreatedAt(now);
        callLog.setUpdatedAt(now);
        callLog.addEvent(CallEvent.of(CallEventType.DIAL_STARTED, "Attempt " + attemptNumber + " started")
                .with("handled_by", handledBy));

        if (recordingEnabled) {
            callLog.setRecordingStatus(RecordingStatus.STARTING);
            callLog.addEvent(CallEvent.of(CallEventType.RECORDING_STARTED, "Recording requested"));
        }

        LeadCallLog saved = callLogRepository.save(callLog);

        lead.setPipelineStatus(LeadPipelineStatus.DIALING);
        lead.setAttemptCount(attemptNumber);
        lead.setLastAttemptAt(now);
        lead.setLastCallLogId(saved.getId());
        lead.setUpdatedAt(now);
        leadRepository.save(lead);

        log.info("Opened attempt {} for lead {}", attemptNumber, lead.getIdAsString());
        return toSession(lead, saved, policy, null);
    }

    private CallSession toSession(Lead lead, LeadCallLog callLog, CallPolicy policy, StartCallRequest request) {
        boolean recordingEnabled = request != null && request.recordingEnabled() != null
                ? request.recordingEnabled()
                : policy.recordingEnabledOrDefault();
        return new CallSession(lead, callLog, recordingEnabled);
    }

    // ----------------------------------------------------------------- outcome

    @Override
    public LeadCallLog recordOutcome(String callLogId, CallOutcomeRequest request) {
        Organization organization = organizationService.current();
        CallPolicy policy = policyOf(organization);

        LeadCallLog callLog = requireLog(callLogId);
        if (callLog.isClosed()) {
            throw new InvalidStateTransitionException(
                    "Attempt " + callLogId + " was already closed as " + callLog.getOutcome().getValue());
        }

        Lead lead = leadRepository.findById(callLog.getLeadId())
                .orElseThrow(() -> ResourceNotFoundException.lead("id", callLog.getLeadIdAsString()));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        CallOutcome outcome = request.outcome();

        callLog.setOutcome(outcome);
        callLog.setEndedAt(now);
        callLog.setUpdatedAt(now);
        callLog.setRingSeconds(request.ringSeconds());
        callLog.setTalkSeconds(request.talkSeconds() != null ? request.talkSeconds() : 0);
        callLog.setSummary(request.summary());
        callLog.setNotes(request.notes());
        callLog.setErrorCode(request.errorCode());
        callLog.setErrorMessage(request.errorMessage());
        callLog.setRequestedCallbackAt(request.requestedCallbackAt());
        if (request.recordingUrl() != null) {
            callLog.setRecordingUrl(request.recordingUrl());
            callLog.setRecordingStatus(RecordingStatus.AVAILABLE);
            callLog.setRecordingReadyAt(now);
            lead.setLastRecordingUrl(request.recordingUrl());
        }
        callLog.addEvent(CallEvent.of(CallEventType.HANGUP, "Call ended as " + outcome.getValue())
                .with("talk_seconds", callLog.getTalkSeconds()));

        applyLeadDetailsFromCall(lead, request);

        lead.setLastOutcome(outcome);
        lead.setLastAttemptAt(now);
        lead.setTotalTalkSeconds(orZero(lead.getTotalTalkSeconds()) + orZero(callLog.getTalkSeconds()));

        if (outcome.isConnected()) {
            callLog.setAnsweredAt(callLog.getAnsweredAt() != null ? callLog.getAnsweredAt() : now);
            lead.setConnectedCount(orZero(lead.getConnectedCount()) + 1);
            lead.setLastConnectedAt(now);
            applyDisposition(lead, callLog, request, organization, policy, now);
        } else {
            applyUnanswered(lead, callLog, outcome, organization, policy, now);
        }

        callLog.setPipelineStatusAfter(lead.getPipelineStatus());
        callLog.addEvent(CallEvent.of(CallEventType.STATUS_CHANGED,
                        "Lead moved to " + lead.getPipelineStatus().getValue())
                .with("final_status", lead.getFinalStatus() == null ? null : lead.getFinalStatus().getValue()));

        LeadCallLog savedLog = callLogRepository.save(callLog);
        lead.setLastCallLogId(savedLog.getId());
        lead.setUpdatedAt(now);
        leadRepository.save(lead);

        log.info("Attempt {} for lead {} closed as {} -> lead is {}",
                callLog.getAttemptNumber(), lead.getIdAsString(), outcome.getValue(),
                lead.getPipelineStatus().getValue());
        return savedLog;
    }

    /**
     * A lead is created with just a name and a number. This is where the call fills in the
     * rest, so after the first conversation the record is complete.
     */
    private void applyLeadDetailsFromCall(Lead lead, CallOutcomeRequest request) {
        if (hasText(request.leadName())) {
            lead.setName(request.leadName().trim());
        }
        if (hasText(request.leadProject())) {
            lead.setProject(request.leadProject().trim());
        }
        if (hasText(request.leadQuery())) {
            lead.setQuery(request.leadQuery().trim());
        }
        if (hasText(request.whatsappPhone())) {
            lead.setWhatsappPhone(PhoneNumbers.normalize(request.whatsappPhone()));
        }
        if (hasText(request.summary())) {
            lead.setNotes(request.summary().trim());
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** The connected path: what the lead agreed to decides where the lead goes next. */
    private void applyDisposition(Lead lead, LeadCallLog callLog, CallOutcomeRequest request,
                                  Organization organization, CallPolicy policy, OffsetDateTime now) {
        CallDisposition disposition = request.disposition();
        if (disposition == null) {
            throw new InvalidLeadPayloadException(
                    "disposition is required when the outcome is 'answered'");
        }

        callLog.setDisposition(disposition);
        lead.setLastDisposition(disposition);
        callLog.addEvent(CallEvent.of(CallEventType.DISPOSITION_SET, disposition.getValue()));

        switch (disposition) {
            case SITE_VISIT_BOOKED -> {
                if (request.siteVisitAt() == null) {
                    throw new InvalidLeadPayloadException(
                            "siteVisitAt is required for disposition 'siteVisitBooked'");
                }
                lead.setActionType(ActionType.SITE_VISIT);
                lead.setStatus(LeadStatus.SCHEDULED);
                lead.setScheduledFor(request.siteVisitAt());
                lead.setConfirmedByLead(Boolean.TRUE);
                if (Boolean.TRUE.equals(lead.getReminderEnabled()) && lead.getReminderDueAt() == null) {
                    lead.setReminderDueAt(request.siteVisitAt().minusMinutes(30));
                }
                close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.SITE_VISIT_BOOKED);
            }
            case CALLBACK_REQUESTED, RESCHEDULED -> {
                if (request.requestedCallbackAt() == null) {
                    throw new InvalidLeadPayloadException(
                            "requestedCallbackAt is required for disposition '" + disposition.getValue() + "'");
                }
                // A callback the lead asked for is honoured regardless of the retry budget:
                // the budget exists to stop us pestering people who never answer, not to cut
                // off someone who is engaged and picked a time.
                lead.setActionType(ActionType.TEAM_CALLBACK);
                lead.setStatus(LeadStatus.SCHEDULED);
                lead.setCallbackAt(request.requestedCallbackAt());
                lead.setConfirmedByLead(Boolean.TRUE);
                lead.setPipelineStatus(LeadPipelineStatus.CALLBACK_SCHEDULED);
                lead.setFinalStatus(null);
                lead.setNextAttemptAt(clampToWindow(request.requestedCallbackAt(), organization, policy));
                callLog.setRetryScheduledFor(lead.getNextAttemptAt());
                callLog.addEvent(CallEvent.of(CallEventType.CALLBACK_REQUESTED, "Callback booked")
                        .with("requested_at", request.requestedCallbackAt().toString())
                        .with("scheduled_at", lead.getNextAttemptAt().toString()));
            }
            case DETAILS_REQUESTED -> {
                lead.setActionType(ActionType.WHATSAPP_PROJECT_DETAILS);
                lead.setStatus(LeadStatus.REQUESTED);
                if (lead.getWhatsappPhone() == null) {
                    lead.setWhatsappPhone(lead.getCallingPhone());
                }
                close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.INTERESTED);
            }
            case INTERESTED -> close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.INTERESTED);
            case NOT_INTERESTED -> close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.NOT_INTERESTED);
            case UNQUALIFIED -> close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.UNQUALIFIED);
            case WRONG_NUMBER -> close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.WRONG_NUMBER);
            case DO_NOT_CALL -> {
                lead.setDoNotCall(Boolean.TRUE);
                close(lead, LeadPipelineStatus.SUPPRESSED, LeadFinalStatus.DO_NOT_CALL);
            }
            case LANGUAGE_BARRIER, NO_DECISION ->
                // Connected but unresolved: try again later, and this one does spend an attempt.
                    scheduleRetry(lead, callLog, CallOutcome.ANSWERED, organization, policy, now);
        }
    }

    /** The unanswered path: retry with backoff, or give up once the budget is spent. */
    private void applyUnanswered(Lead lead, LeadCallLog callLog, CallOutcome outcome,
                                 Organization organization, CallPolicy policy, OffsetDateTime now) {
        if (outcome.isPermanentFailure()) {
            close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.WRONG_NUMBER);
            callLog.addEvent(CallEvent.of(CallEventType.OUTCOME_RECORDED,
                    "Number is not reachable at all; lead closed"));
            return;
        }
        if (!outcome.isRetryable()) {
            close(lead, LeadPipelineStatus.COMPLETED, null);
            return;
        }
        scheduleRetry(lead, callLog, outcome, organization, policy, now);
    }

    private void scheduleRetry(Lead lead, LeadCallLog callLog, CallOutcome outcome,
                               Organization organization, CallPolicy policy, OffsetDateTime now) {
        int maxAttempts = policy.maxAttemptsOrDefault();
        if (lead.attemptCountOrZero() >= maxAttempts) {
            lead.setNextAttemptAt(null);
            close(lead, LeadPipelineStatus.EXHAUSTED, LeadFinalStatus.UNREACHABLE);
            callLog.addEvent(CallEvent.of(CallEventType.RETRIES_EXHAUSTED,
                            "No answer after " + lead.attemptCountOrZero() + " attempts")
                    .with("max_attempts", maxAttempts));
            return;
        }

        OffsetDateTime nextAttempt = clampToWindow(
                now.plusMinutes(policy.backoffMinutesFor(outcome)), organization, policy);

        lead.setPipelineStatus(LeadPipelineStatus.RETRY_SCHEDULED);
        lead.setFinalStatus(null);
        lead.setNextAttemptAt(nextAttempt);
        callLog.setRetryScheduledFor(nextAttempt);
        callLog.addEvent(CallEvent.of(CallEventType.RETRY_SCHEDULED,
                        "Retry " + (lead.attemptCountOrZero() + 1) + " of " + maxAttempts)
                .with("next_attempt_at", nextAttempt.toString())
                .with("backoff_minutes", policy.backoffMinutesFor(outcome)));
    }

    private void close(Lead lead, LeadPipelineStatus pipelineStatus, LeadFinalStatus finalStatus) {
        lead.setPipelineStatus(pipelineStatus);
        lead.setFinalStatus(finalStatus);
        lead.setNextAttemptAt(null);
    }

    // -------------------------------------------------------------- reschedule

    @Override
    public Lead reschedule(String leadId, RescheduleRequest request) {
        Organization organization = organizationService.current();
        CallPolicy policy = policyOf(organization);

        Lead lead = requireLead(leadId);

        if (lead.isDoNotCall()) {
            throw new InvalidStateTransitionException(
                    "Lead " + leadId + " is marked do_not_call and cannot be rescheduled");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime scheduledAt = clampToWindow(request.requestedAt(), organization, policy);

        lead.setPipelineStatus(LeadPipelineStatus.CALLBACK_SCHEDULED);
        lead.setFinalStatus(null);
        lead.setNextAttemptAt(scheduledAt);
        lead.setActionType(ActionType.TEAM_CALLBACK);
        lead.setStatus(LeadStatus.RESCHEDULED);
        lead.setCallbackAt(request.requestedAt());
        lead.setUpdatedAt(now);

        // Reschedules are part of the follow-up story, so they get their own audit row.
        LeadCallLog entry = new LeadCallLog();
        entry.setLeadId(lead.getId());
        entry.setPhone(lead.getCallingPhone());
        entry.setName(lead.getName());
        entry.setProject(lead.getProject());
        entry.setAttemptNumber(0);
        entry.setDirection(SYSTEM_DIRECTION);
        entry.setHandledBy(currentActor.actor());
        entry.setDisposition(CallDisposition.RESCHEDULED);
        entry.setPipelineStatusBefore(lead.getPipelineStatus());
        entry.setPipelineStatusAfter(LeadPipelineStatus.CALLBACK_SCHEDULED);
        entry.setRequestedCallbackAt(request.requestedAt());
        entry.setRetryScheduledFor(scheduledAt);
        entry.setNotes(request.notes());
        entry.setCreatedAt(now);
        entry.setUpdatedAt(now);
        entry.addEvent(CallEvent.of(CallEventType.CALLBACK_REQUESTED, "Rescheduled by request")
                .with("requested_at", request.requestedAt().toString())
                .with("scheduled_at", scheduledAt.toString()));
        callLogRepository.save(entry);

        return leadRepository.save(lead);
    }

    // ---------------------------------------------------------------- watchdog

    @Override
    public int releaseStuckAttempts() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(dialerProperties.getStaleDialMinutes());

        Query query = Query.query(Criteria.where("pipeline_status").is(LeadPipelineStatus.DIALING.getValue())
                .and("updated_at").lt(Date.from(cutoff.toInstant())));

        Update update = new Update()
                .set("pipeline_status", LeadPipelineStatus.QUEUED.getValue())
                .set("next_attempt_at", Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
                .set("updated_at", Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()));

        long released = mongoTemplate.updateMulti(query, update, Lead.class).getModifiedCount();
        if (released > 0) {
            log.warn("Released {} lead(s) stuck in dialing", released);
        }
        return (int) released;
    }

    @Override
    public long dueCount() {
        Date now = Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant());
        Query query = Query.query(Criteria.where("do_not_call").ne(Boolean.TRUE)
                .and("pipeline_status").in(CLAIMABLE)
                .and("next_attempt_at").lte(now));
        return mongoTemplate.count(query, Lead.class);
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Pushes a time into the organization's calling window: before it, move to today's opening;
     * after it, move to tomorrow's opening. Keeps the dialler inside legal calling hours.
     */
    private OffsetDateTime clampToWindow(OffsetDateTime candidate, Organization organization, CallPolicy policy) {
        ZoneId zone = zoneOf(organization);
        ZonedDateTime local = candidate.atZoneSameInstant(zone);
        LocalTime start = policy.windowStart();
        LocalTime end = policy.windowEnd();

        if (local.toLocalTime().isBefore(start)) {
            return local.with(start).toOffsetDateTime();
        }
        if (local.toLocalTime().isAfter(end)) {
            return local.plusDays(1).with(start).toOffsetDateTime();
        }
        return candidate;
    }

    private void deferToNextWindow(Lead lead, Organization organization, CallPolicy policy) {
        ZoneId zone = zoneOf(organization);
        ZonedDateTime tomorrow = OffsetDateTime.now(ZoneOffset.UTC)
                .atZoneSameInstant(zone)
                .plusDays(1)
                .with(policy.windowStart());

        lead.setPipelineStatus(LeadPipelineStatus.RETRY_SCHEDULED);
        lead.setNextAttemptAt(tomorrow.toOffsetDateTime());
        lead.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        leadRepository.save(lead);
        log.debug("Lead {} hit the daily attempt cap; deferred to {}", lead.getIdAsString(), tomorrow);
    }

    private long attemptsToday(Lead lead, Organization organization) {
        ZoneId zone = zoneOf(organization);
        OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC)
                .atZoneSameInstant(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toOffsetDateTime();
        return callLogRepository.countByLeadIdAndDialStartedAtGreaterThanEqual(lead.getId(), startOfDay);
    }

    private ZoneId zoneOf(Organization organization) {
        try {
            return organization.getTimezone() == null || organization.getTimezone().isBlank()
                    ? ZoneId.of("Asia/Kolkata")
                    : ZoneId.of(organization.getTimezone());
        } catch (RuntimeException ex) {
            log.warn("Organization {} has an invalid timezone '{}'; falling back to Asia/Kolkata",
                    organization.getIdAsString(), organization.getTimezone());
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private CallPolicy policyOf(Organization organization) {
        return organization.getCallPolicy() != null ? organization.getCallPolicy() : CallPolicy.defaults();
    }

    private Lead requireLead(String leadId) {
        if (!ObjectId.isValid(leadId)) {
            throw ResourceNotFoundException.lead("id", leadId);
        }
        return leadRepository.findById(new ObjectId(leadId))
                .orElseThrow(() -> ResourceNotFoundException.lead("id", leadId));
    }

    private LeadCallLog requireLog(String callLogId) {
        if (!ObjectId.isValid(callLogId)) {
            throw ResourceNotFoundException.callLog("id", callLogId);
        }
        return callLogRepository.findById(new ObjectId(callLogId))
                .orElseThrow(() -> ResourceNotFoundException.callLog("id", callLogId));
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
