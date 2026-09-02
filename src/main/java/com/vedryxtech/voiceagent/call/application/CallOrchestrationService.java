package com.vedryxtech.voiceagent.call.application;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.api.dto.CallOutcomeRequest;
import com.vedryxtech.voiceagent.call.api.dto.RescheduleRequest;
import com.vedryxtech.voiceagent.call.api.dto.StartCallRequest;

import java.util.List;

/**
 * The dialling loop, as a state machine.
 *
 * <pre>
 *   claimNext / startCall        recordOutcome
 *   ────────────────────►  DIALING  ──────────────►  answered?
 *                                                    │
 *        ┌───────────────────────────────────────────┴──────────────────────┐
 *        │ no                                                            yes│
 *        ▼                                                                  ▼
 *   retryable && attempts < max ?                              disposition decides
 *        │ yes            │ no                                  ├─ site visit  -> COMPLETED (won)
 *        ▼                ▼                                     ├─ callback    -> CALLBACK_SCHEDULED
 *   RETRY_SCHEDULED   EXHAUSTED                                 ├─ not interested -> COMPLETED (lost)
 *   (+backoff,        (final:                                   ├─ do not call -> SUPPRESSED
 *    calling window)   unreachable)                             └─ no decision -> RETRY_SCHEDULED
 * </pre>
 *
 * <p>Every transition appends to {@code leads_log}: the lead document holds only where things
 * stand now, the log holds how it got there.</p>
 */
public interface CallOrchestrationService {

    /**
     * Atomically claims up to {@code limit} leads that are due, flips them to
     * {@code dialing} and opens an attempt for each. Two workers polling at the same time
     * never receive the same lead.
     */
    List<CallSession> claimNext(int limit);

    /** Opens an attempt for one specific lead, e.g. the dashboard's "call now" button. */
    CallSession startCall(String leadId, StartCallRequest request);

    /** Closes the attempt after hangup and moves the lead. This is the only status writer. */
    LeadCallLog recordOutcome(String callLogId, CallOutcomeRequest request);

    /**
     * "Call me Thursday at 6", captured outside a live call. Books the callback without
     * consuming a retry attempt.
     */
    Lead reschedule(String leadId, RescheduleRequest request);

    /** Releases leads stuck in {@code dialing} because a worker died mid-call. */
    int releaseStuckAttempts();

    /** How many leads are due right now for the caller's organization. */
    long dueCount();

    /** A claimed lead plus the attempt that was opened for it. */
    record CallSession(
            Lead lead,
            LeadCallLog callLog,
            boolean recordingEnabled,
            com.vedryxtech.voiceagent.call.api.dto.CallContext context
    ) {
        /** A session with no history yet — a first call, or a test. */
        public CallSession(Lead lead, LeadCallLog callLog, boolean recordingEnabled) {
            this(lead, callLog, recordingEnabled, null);
        }
    }
}
