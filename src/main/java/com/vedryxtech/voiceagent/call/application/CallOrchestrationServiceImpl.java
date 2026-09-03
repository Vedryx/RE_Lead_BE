package com.vedryxtech.voiceagent.call.application;

import com.vedryxtech.voiceagent.config.CallPolicyProperties;
import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.api.dto.CallContext;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallEvent;
import com.vedryxtech.voiceagent.call.domain.CallEventType;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.call.api.dto.CallOutcomeRequest;
import com.vedryxtech.voiceagent.call.api.dto.RescheduleRequest;
import com.vedryxtech.voiceagent.call.api.dto.StartCallRequest;
import com.vedryxtech.voiceagent.exception.InvalidLeadPayloadException;
import com.vedryxtech.voiceagent.exception.InvalidStateTransitionException;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.call.persistence.LeadCallLogRepository;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import com.vedryxtech.voiceagent.storage.CallArtifactService;
import com.vedryxtech.voiceagent.call.domain.TranscriptTurn;
import com.vedryxtech.voiceagent.security.CurrentActor;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
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

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
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
    /**
     * Stages the agent may dial, whatever the schedule says. A lead a human has moved on
     * — to CALLBACK_REQUESTED, SITE_VISIT or DISCARDED — is theirs, and an agent dialling
     * in behind them contradicts a colleague in front of a customer.
     *
     * <p>This is a second, independent filter. {@code stage} answers "may the agent speak
     * to this person at all"; {@code pipelineStatus} answers "is there work due now".
     * Note CALLBACK_SCHEDULED is claimable and is set for both kinds of callback, so
     * without the stage filter a lead handed to a person would be dialled the moment
     * their callback time arrived.
     */
    private static final List<String> AGENT_CALLABLE_STAGES = List.of(
            LeadStage.NEW.getValue(),
            LeadStage.FOLLOW_UP.getValue());

    private static final List<String> CLAIMABLE = List.of(
            LeadPipelineStatus.NEW.getValue(),
            LeadPipelineStatus.QUEUED.getValue(),
            LeadPipelineStatus.RETRY_SCHEDULED.getValue(),
            LeadPipelineStatus.CALLBACK_SCHEDULED.getValue());

    private final LeadRepository leadRepository;
    private final LeadCallLogRepository callLogRepository;
    private final SettingsService settingsService;
    private final MongoTemplate mongoTemplate;
    private final CallPolicyProperties dialerProperties;
    private final CurrentActor currentActor;
    private final CallArtifactService artifacts;

    public CallOrchestrationServiceImpl(LeadRepository leadRepository,
                                        LeadCallLogRepository callLogRepository,
                                        SettingsService settingsService,
                                        MongoTemplate mongoTemplate,
                                        CallPolicyProperties dialerProperties,
                                        CurrentActor currentActor,
                                        CallArtifactService artifacts) {
        this.leadRepository = leadRepository;
        this.callLogRepository = callLogRepository;
        this.settingsService = settingsService;
        this.mongoTemplate = mongoTemplate;
        this.dialerProperties = dialerProperties;
        this.currentActor = currentActor;
        this.artifacts = artifacts;
    }

    // ------------------------------------------------------------------ claim

    @Override
    public List<CallSession> claimNext(int limit) {
        AppSettings settings = settingsService.current();
        CallPolicy policy = policyOf(settings);

        int batch = Math.min(Math.max(limit, 1), dialerProperties.getBatchSize());
        List<CallSession> sessions = new ArrayList<>(batch);

        // "Call now" first: a person is watching a button and expects the phone to ring,
        // and these bypass both the schedule and the stage gate on purpose.
        sessions.addAll(claimUndialledAttempts(batch, policy));

        for (int i = sessions.size(); i < batch; i++) {
            Lead claimed = claimOne();
            if (claimed == null) {
                break;
            }
            if (attemptsToday(claimed, settings) >= policy.maxAttemptsPerDayOrDefault()) {
                // Over the daily cap: put it back with tomorrow's window instead of dialling.
                deferToNextWindow(claimed, settings, policy);
                continue;
            }
            sessions.add(openAttempt(claimed, settings, policy, null, AI_AGENT, null, true));
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
                        .and("stage").in(AGENT_CALLABLE_STAGES)
                        .and("pipeline_status").in(CLAIMABLE)
                        .and("project").nin(NO_PROJECT)
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
        AppSettings settings = settingsService.current();
        CallPolicy policy = policyOf(settings);

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

        // No daily-cap check here, deliberately. claimNext defers a lead over
        // maxAttemptsPerDay; a person pressing the button has decided this one is worth
        // an extra call, and the cap exists to stop us pestering people automatically.
        lead.setPipelineStatus(LeadPipelineStatus.DIALING);
        lead.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lead = leadRepository.save(lead);

        String handledBy = request != null && request.handledBy() != null
                ? request.handledBy()
                : currentActor.actor();

        // dialStarting = false: this queues the call, it does not place it. The dialler
        // claims it on its next pass, which is why startCall answers 202 and not 200.
        return openAttempt(lead, settings, policy, idempotencyKey, handledBy,
                request == null ? null : request.recordingEnabled(), false);
    }

    /** How many earlier calls reach the prompt. Three is enough to sound like someone
     *  who remembers, and it bounds what goes into a frozen system instruction. */
    private static final int PRIOR_CALLS_IN_CONTEXT = 3;

    /** A lead with no project names no knowledge base, so the agent has nothing to say.
     *  Handing one out would mark it dialing, get refused, and come back on the next
     *  sweep for ever. Leaving it unclaimed makes it visible in the CRM instead. */
    private static final List<Object> NO_PROJECT = Arrays.asList("", null);

    /**
     * What the agent should know before it dials.
     *
     * <p>Only calls that actually happened count: the audit rows a reschedule writes have
     * no outcome and would read as a silent conversation.
     */
    /**
     * Keep what was said, in both places it belongs.
     *
     * <p>Mongo is the index — it makes the words searchable. The archive copy beside the
     * audio is written later, once the outcome is fully decided, so the file carries the
     * disposition rather than a null.
     */
    private void keepTranscript(LeadCallLog callLog, CallOutcomeRequest request) {
        if (request.transcript() == null || request.transcript().isEmpty()) {
            return;
        }
        List<TranscriptTurn> turns = request.transcript().stream()
                .map(turn -> new TranscriptTurn(turn.role(), turn.text(), turn.atSeconds()))
                .toList();
        callLog.setTranscript(turns);
        // The agent sends the count before truncation. Falling back to what arrived keeps
        // the field honest rather than absent when an older agent posts.
        callLog.setTranscriptTurnCount(request.transcriptTurnCount() != null
                ? request.transcriptTurnCount()
                : turns.size());
        callLog.addEvent(CallEvent.of(CallEventType.HANGUP,
                "Transcript captured: " + turns.size() + " turn(s)"));
    }

    private CallContext contextFor(Lead lead, LeadCallLog current) {
        List<CallContext.PriorCall> priors = callLogRepository
                .findByLeadIdOrderByAttemptNumberDesc(lead.getId()).stream()
                .filter(log -> current == null || !log.getId().equals(current.getId()))
                .filter(log -> log.getOutcome() != null)
                .limit(PRIOR_CALLS_IN_CONTEXT)
                .map(log -> new CallContext.PriorCall(
                        log.getEndedAt(), log.getOutcome(), log.getDisposition(), log.getSummary()))
                .toList();

        return new CallContext(
                lead.getProject(),
                lead.stageOrNew(),
                lead.getActionType(),
                lead.getScheduledFor(),
                lead.getCallbackAt(),
                lead.getQuery(),
                lead.getWhatsappPhone(),
                orZero(lead.getAttemptCount()),
                orZero(lead.getConnectedCount()),
                priors);
    }

    /**
     * Hand out attempts that exist but nobody has dialled — the rows "Call now" creates.
     *
     * <p>Single-shot the same way the scheduled path is: {@code findAndModify} stamps
     * {@code dial_started_at} as it returns the row, so a second dialler sees it taken.
     *
     * <p>Two filters matter as much as the null check. {@code direction = outbound}
     * excludes the audit rows {@code reschedule()} writes, which also carry no
     * timestamps — without it, every manual reschedule would dial the lead. And the
     * staleness bound stops an attempt orphaned by a dead dialler being picked up hours
     * later, ringing someone about a button pressed that morning.
     */
    private List<CallSession> claimUndialledAttempts(int limit, CallPolicy policy) {
        List<CallSession> sessions = new ArrayList<>();
        Date floor = Date.from(OffsetDateTime.now(ZoneOffset.UTC)
                .minusMinutes(dialerProperties.getStaleDialMinutes()).toInstant());

        for (int i = 0; i < limit; i++) {
            Date now = Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant());
            Query query = Query.query(Criteria.where("dial_started_at").is(null)
                            .and("ended_at").is(null)
                            .and("direction").is("outbound")
                            .and("created_at").gt(floor))
                    .with(Sort.by(Sort.Direction.ASC, "created_at"));

            LeadCallLog claimed = mongoTemplate.findAndModify(
                    query, new Update().set("dial_started_at", now).set("updated_at", now),
                    FindAndModifyOptions.options().returnNew(true), LeadCallLog.class);
            if (claimed == null) {
                break;
            }
            Lead lead = leadRepository.findById(claimed.getLeadId()).orElse(null);
            if (lead == null) {
                continue;
            }
            sessions.add(new CallSession(lead, claimed,
                    claimed.getRecordingStatus() != null || policy.recordingEnabledOrDefault(),
                    contextFor(lead, claimed)));
        }
        if (!sessions.isEmpty()) {
            log.info("Handed out {} manually started call(s) ahead of the schedule", sessions.size());
        }
        return sessions;
    }

    /** Creates the {@code leads_log} row for one attempt. */
    private CallSession openAttempt(Lead lead, AppSettings settings, CallPolicy policy,
                                    String idempotencyKey, String handledBy,
                                    Boolean recordingOverride, boolean dialStarting) {
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
        // Only stamped when a dialler is actually taking the row. "Call now" opens an
        // attempt nobody has dialled yet, and a null dial_started_at is exactly how the
        // dialler finds it. Stamping here would make every attempt look already-taken,
        // and the manual queue would return nothing forever.
        if (dialStarting) {
            callLog.setDialStartedAt(now);
        }
        callLog.setCreatedAt(now);
        callLog.setUpdatedAt(now);
        callLog.addEvent(CallEvent.of(CallEventType.DIAL_STARTED, "Attempt " + attemptNumber + " started")
                .with("handled_by", handledBy));

        if (recordingEnabled) {
            callLog.setRecordingStatus(RecordingStatus.STARTING);
            callLog.addEvent(CallEvent.of(CallEventType.RECORDING_STARTED, "Recording requested"));
        }

        LeadCallLog saved = callLogRepository.save(callLog);
        // The id only exists after the first save, and both keys are built from it. Doing
        // this now rather than at teardown is what lets the agent tell egress where to put
        // the audio instead of learning the location from a webhook minutes later.
        artifacts.assignKeys(saved, lead.getProject());
        saved = callLogRepository.save(saved);

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
        return new CallSession(lead, callLog, recordingEnabled, contextFor(lead, callLog));
    }

    // ----------------------------------------------------------------- outcome

    @Override
    public LeadCallLog recordOutcome(String callLogId, CallOutcomeRequest request) {
        AppSettings settings = settingsService.current();
        CallPolicy policy = policyOf(settings);

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
        keepTranscript(callLog, request);
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
            applyDisposition(lead, callLog, request, settings, policy, now);
        } else {
            applyUnanswered(lead, callLog, outcome, settings, policy, now);
        }

        callLog.setPipelineStatusAfter(lead.getPipelineStatus());
        callLog.addEvent(CallEvent.of(CallEventType.STATUS_CHANGED,
                        "Lead moved to " + lead.getPipelineStatus().getValue())
                .with("final_status", lead.getFinalStatus() == null ? null : lead.getFinalStatus().getValue()));

        LeadCallLog savedLog = callLogRepository.save(callLog);
        // Archived last, on the saved log: the file is meant to be readable without the
        // database that produced it, and the disposition is only decided above this line.
        artifacts.archiveTranscript(savedLog, savedLog.getTranscript());
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
                                  AppSettings settings, CallPolicy policy, OffsetDateTime now) {
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
                advanceStage(lead, LeadStage.SITE_VISIT);
                lead.setScheduledFor(request.siteVisitAt());
                lead.setConfirmedByLead(Boolean.TRUE);
                // Reminders are a person's decision now, but they still need to know which
                // visits are coming up. Nothing sets reminderEnabled on a lead the agent
                // created, so without this reminderDueAt stays null by construction and the
                // reminder worklist is empty for exactly the visits it exists to show.
                if (lead.getReminderEnabled() == null) {
                    lead.setReminderEnabled(Boolean.TRUE);
                }
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
                //
                // Which kind of callback matters: one the agent will make itself keeps the
                // lead in FOLLOW_UP and dialable, one handed to a person moves it to
                // CALLBACK_REQUESTED where the agent must not touch it. The agent tells us
                // in actionType; before that field existed this branch always guessed
                // "team", so every agent follow-up was filed as a human's job.
                ActionType callbackKind = request.actionType() == ActionType.FOLLOW_UP_CALL
                        ? ActionType.FOLLOW_UP_CALL
                        : ActionType.TEAM_CALLBACK;
                lead.setActionType(callbackKind);
                advanceStage(lead, callbackKind == ActionType.FOLLOW_UP_CALL
                        ? LeadStage.FOLLOW_UP
                        : LeadStage.CALLBACK_REQUESTED);
                lead.setStatus(LeadStatus.SCHEDULED);
                // FOLLOW_UP_CALL is the one ActionType whose validator demands scheduledFor
                // (LeadServiceImpl.validate). The callback time is the scheduled time, so
                // set both rather than leave the lead failing its own validation later.
                if (callbackKind == ActionType.FOLLOW_UP_CALL) {
                    lead.setScheduledFor(request.requestedCallbackAt());
                }
                lead.setCallbackAt(request.requestedCallbackAt());
                lead.setConfirmedByLead(Boolean.TRUE);
                lead.setPipelineStatus(LeadPipelineStatus.CALLBACK_SCHEDULED);
                lead.setFinalStatus(null);
                lead.setNextAttemptAt(clampToWindow(request.requestedCallbackAt(), settings, policy));
                callLog.setRetryScheduledFor(lead.getNextAttemptAt());
                callLog.addEvent(CallEvent.of(CallEventType.CALLBACK_REQUESTED, "Callback booked")
                        .with("requested_at", request.requestedCallbackAt().toString())
                        .with("scheduled_at", lead.getNextAttemptAt().toString()));
            }
            case DETAILS_REQUESTED -> {
                lead.setActionType(ActionType.WHATSAPP_PROJECT_DETAILS);
                lead.setStatus(LeadStatus.REQUESTED);
                advanceStage(lead, LeadStage.FOLLOW_UP);
                if (lead.getWhatsappPhone() == null) {
                    lead.setWhatsappPhone(lead.getCallingPhone());
                }
                // Deliberately not closed. Asking for details is interest, not a decision,
                // and closing here nulls nextAttemptAt so the lead would sit in FOLLOW_UP
                // looking active and never be dialled again. Nothing sends the details
                // yet either, so this lead is owed both a message and a call.
                scheduleRetry(lead, callLog, CallOutcome.ANSWERED, settings, policy, now);
            }
            case INTERESTED -> {
                // Interested but nothing agreed: still ours to chase, not a discard.
                advanceStage(lead, LeadStage.FOLLOW_UP);
                close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.INTERESTED);
            }
            case NOT_INTERESTED -> {
                advanceStage(lead, LeadStage.DISCARDED);
                close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.NOT_INTERESTED);
            }
            case UNQUALIFIED -> {
                advanceStage(lead, LeadStage.DISCARDED);
                close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.UNQUALIFIED);
            }
            case WRONG_NUMBER -> {
                advanceStage(lead, LeadStage.DISCARDED);
                close(lead, LeadPipelineStatus.COMPLETED, LeadFinalStatus.WRONG_NUMBER);
            }
            case DO_NOT_CALL -> {
                lead.setDoNotCall(Boolean.TRUE);
                advanceStage(lead, LeadStage.DISCARDED);
                close(lead, LeadPipelineStatus.SUPPRESSED, LeadFinalStatus.DO_NOT_CALL);
            }
            case LANGUAGE_BARRIER, NO_DECISION ->
                // Connected but unresolved: try again later, and this one does spend an attempt.
                    scheduleRetry(lead, callLog, CallOutcome.ANSWERED, settings, policy, now);
        }
    }

    /** The unanswered path: retry with backoff, or give up once the budget is spent. */
    private void applyUnanswered(Lead lead, LeadCallLog callLog, CallOutcome outcome,
                                 AppSettings settings, CallPolicy policy, OffsetDateTime now) {
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
        scheduleRetry(lead, callLog, outcome, settings, policy, now);
    }

    private void scheduleRetry(Lead lead, LeadCallLog callLog, CallOutcome outcome,
                               AppSettings settings, CallPolicy policy, OffsetDateTime now) {
        int maxAttempts = policy.maxAttemptsOrDefault();
        if (lead.attemptCountOrZero() >= maxAttempts) {
            lead.setNextAttemptAt(null);
            advanceStage(lead, LeadStage.DISCARDED);
            close(lead, LeadPipelineStatus.EXHAUSTED, LeadFinalStatus.UNREACHABLE);
            callLog.addEvent(CallEvent.of(CallEventType.RETRIES_EXHAUSTED,
                            "No answer after " + lead.attemptCountOrZero() + " attempts")
                    .with("max_attempts", maxAttempts));
            return;
        }

        OffsetDateTime nextAttempt = clampToWindow(
                now.plusMinutes(policy.backoffMinutesFor(outcome)), settings, policy);

        lead.setPipelineStatus(LeadPipelineStatus.RETRY_SCHEDULED);
        lead.setFinalStatus(null);
        lead.setNextAttemptAt(nextAttempt);
        callLog.setRetryScheduledFor(nextAttempt);
        callLog.addEvent(CallEvent.of(CallEventType.RETRY_SCHEDULED,
                        "Retry " + (lead.attemptCountOrZero() + 1) + " of " + maxAttempts)
                .with("next_attempt_at", nextAttempt.toString())
                .with("backoff_minutes", policy.backoffMinutesFor(outcome)));
    }

    /**
     * Move the funnel stage for an agent-reported outcome. Ratcheted: forward or out,
     * never backwards, so a missed reminder call cannot undo a booked visit.
     */
    private void advanceStage(Lead lead, LeadStage next) {
        lead.setStage(lead.stageOrNew().advanceTo(next));
    }

    private void close(Lead lead, LeadPipelineStatus pipelineStatus, LeadFinalStatus finalStatus) {
        lead.setPipelineStatus(pipelineStatus);
        lead.setFinalStatus(finalStatus);
        lead.setNextAttemptAt(null);
    }

    // -------------------------------------------------------------- reschedule

    @Override
    public Lead reschedule(String leadId, RescheduleRequest request) {
        AppSettings settings = settingsService.current();
        CallPolicy policy = policyOf(settings);

        Lead lead = requireLead(leadId);

        if (lead.isDoNotCall()) {
            throw new InvalidStateTransitionException(
                    "Lead " + leadId + " is marked do_not_call and cannot be rescheduled");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime scheduledAt = clampToWindow(request.requestedAt(), settings, policy);

        lead.setPipelineStatus(LeadPipelineStatus.CALLBACK_SCHEDULED);
        lead.setFinalStatus(null);
        lead.setNextAttemptAt(scheduledAt);
        // A person using this endpoint is booking a call, not volunteering to make one.
        // Filing it as TEAM_CALLBACK would move the lead to CALLBACK_REQUESTED, which the
        // agent may not dial — and then nobody would call at the time just agreed.
        lead.setActionType(ActionType.FOLLOW_UP_CALL);
        lead.setScheduledFor(request.requestedAt());
        advanceStage(lead, LeadStage.FOLLOW_UP);
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
        // Identical filter to the claim, or the count advertises work that will never
        // be handed out.
        Query query = Query.query(Criteria.where("do_not_call").ne(Boolean.TRUE)
                .and("stage").in(AGENT_CALLABLE_STAGES)
                .and("pipeline_status").in(CLAIMABLE)
                .and("project").nin(NO_PROJECT)
                .and("next_attempt_at").lte(now));
        return mongoTemplate.count(query, Lead.class);
    }

    // ----------------------------------------------------------------- helpers

    /**
     * Pushes a time into the installation's calling window: before it, move to today's opening;
     * after it, move to tomorrow's opening. Keeps the dialler inside legal calling hours.
     */
    private OffsetDateTime clampToWindow(OffsetDateTime candidate, AppSettings settings, CallPolicy policy) {
        ZoneId zone = zoneOf(settings);
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

    private void deferToNextWindow(Lead lead, AppSettings settings, CallPolicy policy) {
        ZoneId zone = zoneOf(settings);
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

    private long attemptsToday(Lead lead, AppSettings settings) {
        ZoneId zone = zoneOf(settings);
        OffsetDateTime startOfDay = OffsetDateTime.now(ZoneOffset.UTC)
                .atZoneSameInstant(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toOffsetDateTime();
        return callLogRepository.countByLeadIdAndDialStartedAtGreaterThanEqual(lead.getId(), startOfDay);
    }

    private ZoneId zoneOf(AppSettings settings) {
        try {
            return settings.getTimezone() == null || settings.getTimezone().isBlank()
                    ? ZoneId.of("Asia/Kolkata")
                    : ZoneId.of(settings.getTimezone());
        } catch (RuntimeException ex) {
            log.warn("app_settings has an invalid timezone '{}'; falling back to Asia/Kolkata",
                    settings.getTimezone());
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private CallPolicy policyOf(AppSettings settings) {
        return settings.getCallPolicy() != null ? settings.getCallPolicy() : CallPolicy.defaults();
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
