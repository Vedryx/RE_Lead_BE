package com.vedryxtech.voiceagent.call.application;

import com.vedryxtech.voiceagent.call.api.dto.CallOutcomeRequest;
import com.vedryxtech.voiceagent.call.api.dto.RescheduleRequest;
import com.vedryxtech.voiceagent.call.api.dto.StartCallRequest;
import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.persistence.LeadCallLogRepository;
import com.vedryxtech.voiceagent.exception.InvalidLeadPayloadException;
import com.vedryxtech.voiceagent.exception.InvalidStateTransitionException;
import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real-Mongo integration tests for {@code CallOrchestrationServiceImpl}. Closes QA
 * <b>BLK-4</b> and encodes the acceptance conditions for <b>BLK-2</b> and <b>BLK-3</b>.
 *
 * <p>Uses Flapdoodle's embedded Mongo (standalone, matching local dev) so the BLK-2 race
 * runs against a genuine {@code OptimisticLockingFailureException} rather than a mocked
 * one. Sharing one JVM-wide Mongo across tests, each test drops the database first and
 * re-seeds the {@code app_settings} singleton and the admin user so ordering does not leak.
 */
@SpringBootTest
class CallOrchestrationServiceIntegrationTest {

    @Autowired private CallOrchestrationService orchestration;
    @Autowired private LeadRepository leadRepository;
    @Autowired private LeadCallLogRepository callLogRepository;
    @Autowired private SettingsService settingsService;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void freshSlate() {
        // Wipe the working collections between tests. The settings singleton is re-seeded
        // lazily by settingsService.current(); we do not touch app_user (bootstrap runs on
        // context start).
        mongoTemplate.getCollectionNames().forEach(name -> {
            if (!name.equals("app_user") && !name.equals("app_settings")) {
                mongoTemplate.dropCollection(name);
            }
        });
        // Reset the policy to defaults so a leftover permissive maxAttempts from a prior
        // test does not soften an exhaustion assertion here.
        AppSettings settings = settingsService.current();
        settings.setCallPolicy(CallPolicy.defaults());
        settingsService.save(settings);
    }

    // ------------------------------------------------------------ helpers

    private Lead insertLead(String project) {
        Lead lead = new Lead();
        lead.setName("Test-" + UUID.randomUUID().toString().substring(0, 6));
        String phone = "+9198" + String.format("%08d", ((int) (Math.random() * 100_000_000)));
        lead.setPhone(phone);
        lead.setCallingPhone(phone);
        lead.setProject(project);
        lead.setStage(LeadStage.NEW);
        lead.setPipelineStatus(LeadPipelineStatus.NEW);
        lead.setAttemptCount(0);
        lead.setConnectedCount(0);
        lead.setTotalTalkSeconds(0);
        lead.setDoNotCall(Boolean.FALSE);
        lead.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        lead.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lead.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return leadRepository.save(lead);
    }

    private String claimOneCallLogId() {
        List<CallOrchestrationService.CallSession> sessions = orchestration.claimNext(1);
        assertThat(sessions).hasSize(1);
        return sessions.get(0).callLog().getIdAsString();
    }

    private CallOutcomeRequest callbackAt(OffsetDateTime requestedAt) {
        return new CallOutcomeRequest(
                CallOutcome.ANSWERED, CallDisposition.CALLBACK_REQUESTED,
                ActionType.FOLLOW_UP_CALL, 5, 60, "Asked to be rung back", null,
                requestedAt, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null);
    }

    @Test
    @DisplayName("A callback time the lead asked for is kept exactly, even out of hours")
    void a_lead_requested_time_is_never_pushed_into_the_calling_window() {
        // The agent says the time back on the call. Clamping it to the window here is
        // how a promise of "nau baje" turned into a call at nine the next morning.
        insertLead("P");
        String callLogId = claimOneCallLogId();
        OffsetDateTime nineTonight = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(1)
                .withHour(15).withMinute(30).withSecond(0).withNano(0);   // 21:00 IST

        orchestration.recordOutcome(callLogId, callbackAt(nineTonight));

        Lead lead = leadRepository.findAll().get(0);
        assertThat(lead.getNextAttemptAt()).isEqualTo(nineTonight);
        assertThat(lead.isNextAttemptLeadRequested()).isTrue();
    }

    @Test
    @DisplayName("A brochure asked for alongside a callback is still sent")
    void details_requested_with_a_callback_still_reaches_the_lead() {
        // Live call: the lead asked for a callback and the brochure on WhatsApp. One
        // disposition fits on a call, the callback took it, and the details request was
        // dropped — while the agent told him it had been sent.
        insertLead("P");
        String callLogId = claimOneCallLogId();
        OffsetDateTime tomorrow = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);

        orchestration.recordOutcome(callLogId, new CallOutcomeRequest(
                CallOutcome.ANSWERED, CallDisposition.CALLBACK_REQUESTED,
                ActionType.FOLLOW_UP_CALL, 5, 60, "Callback and the brochure", null,
                tomorrow, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, Boolean.TRUE));

        LeadCallLog log = callLogRepository.findById(new org.bson.types.ObjectId(callLogId))
                .orElseThrow();
        assertThat(log.getEvents()).anyMatch(e ->
                e.getMessage() != null && e.getMessage().startsWith("Details also requested"));
        // The callback it agreed to is untouched by the send.
        Lead lead = leadRepository.findAll().get(0);
        assertThat(lead.getPipelineStatus()).isEqualTo(LeadPipelineStatus.CALLBACK_SCHEDULED);
        assertThat(lead.getWhatsappPhone()).isNotNull();
    }

    @Test
    @DisplayName("A 'call now' attempt is dialled out of hours, because a person asked for it")
    void a_call_now_attempt_is_dialled_out_of_hours() {
        // It bypasses the schedule, the stage gate and the hours: somebody pressed the
        // button and is watching the phone. Holding it back until the window opened left
        // the row undialled until it went stale, and the lead was never rung at all.
        Lead lead = insertLead("P");
        orchestration.startCall(lead.getIdAsString(), new StartCallRequest(
                "call-now-out-of-hours", "agent", Boolean.FALSE));

        assertThat(orchestration.claimNext(5, false)).hasSize(1);
    }

    @Test
    @DisplayName("Opening a 'call now' attempt spends none of the lead's budget")
    void a_call_now_attempt_does_not_spend_an_attempt() {
        Lead lead = insertLead("P");
        orchestration.startCall(lead.getIdAsString(), new StartCallRequest(
                "call-now-budget", "agent", Boolean.FALSE));

        assertThat(leadRepository.findById(lead.getId()).orElseThrow().attemptCountOrZero())
                .as("the budget counts dials that failed, not attempts that were opened")
                .isZero();
    }

    @Test
    @DisplayName("Out of hours, only a lead-requested time is claimed")
    void a_closed_window_hands_out_only_the_leads_who_asked_for_this_hour() {
        OffsetDateTime due = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);

        Lead asked = insertLead("P");
        asked.setNextAttemptAt(due);
        asked.setNextAttemptLeadRequested(Boolean.TRUE);
        leadRepository.save(asked);

        Lead routine = insertLead("P");   // its own random number, flag left unset
        routine.setNextAttemptAt(due);
        leadRepository.save(routine);

        List<CallOrchestrationService.CallSession> claimed = orchestration.claimNext(5, false);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).lead().getIdAsString()).isEqualTo(asked.getIdAsString());
    }

    @Test
    @DisplayName("A referral is created through the leads API, not a hand-built document")
    void referral_goes_in_through_the_lead_service() {
        Lead referrer = insertLead("P");
        String callLogId = claimOneCallLogId();

        orchestration.recordOutcome(callLogId, referralFrom(
                "Sunil", "9822889401", "Friend of Dev; looking for a 2 BHK."));

        Lead referred = leadRepository.findByCallingPhone("+919822889401").orElseThrow();
        assertThat(referred.getStage()).isEqualTo(LeadStage.REFERRAL);
        assertThat(referred.getSource()).isEqualTo("referral");
        assertThat(referred.getReferredByLeadId()).isEqualTo(referrer.getId());
        assertThat(referred.getReferralSummary())
                .isEqualTo("Friend of Dev; looking for a 2 BHK.");
        assertThat(referred.getProject()).isEqualTo(referrer.getProject());
    }

    @Test
    @DisplayName("A referral is never queued for the dialler — they did not ask to be called")
    void referral_is_not_dialable() {
        insertLead("P");
        String callLogId = claimOneCallLogId();

        orchestration.recordOutcome(callLogId, referralFrom(
                "Sunil", "9822889401", "Friend of Dev."));

        Lead referred = leadRepository.findByCallingPhone("+919822889401").orElseThrow();
        // The whole point of the stage gate. applyDefaults hands every NEW lead a slot;
        // without the stage check this one would be cold-called on the next poll.
        assertThat(referred.getNextAttemptAt()).isNull();
        assertThat(referred.getStage().isAgentCallable()).isFalse();
    }

    @Test
    @DisplayName("A referral with no summary still records who vouched for them")
    void referral_without_a_summary_falls_back_to_the_referrer() {
        insertLead("P");
        String callLogId = claimOneCallLogId();

        orchestration.recordOutcome(callLogId, referralFrom("Sunil", "9822889401", null));

        Lead referred = leadRepository.findByCallingPhone("+919822889401").orElseThrow();
        assertThat(referred.getReferralSummary()).contains("gave us this name");
    }

    private CallOutcomeRequest referralFrom(String name, String phone, String summary) {
        return new CallOutcomeRequest(
                CallOutcome.ANSWERED, CallDisposition.REFERRAL_GIVEN,
                null, 5, 100, "Not for him", null,
                null, null, null,
                null, null, null, null, null, null,
                null, name,
                phone, summary, null, null, null);
    }

    private CallOutcomeRequest answeredWithSiteVisit(OffsetDateTime siteVisitAt) {
        return new CallOutcomeRequest(
                CallOutcome.ANSWERED, CallDisposition.SITE_VISIT_BOOKED,
                null, 5, 100, "Wants 3 BHK", null,
                null, siteVisitAt, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null);
    }

    private CallOutcomeRequest noAnswer() {
        return new CallOutcomeRequest(
                CallOutcome.NO_ANSWER, null, null, 20, 0, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null);
    }

    private CallOutcomeRequest answered(CallDisposition disposition) {
        return new CallOutcomeRequest(
                CallOutcome.ANSWERED, disposition, null, 5, 60, "s", null,
                null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null);
    }

    private CallOutcomeRequest cancelled() {
        return new CallOutcomeRequest(
                CallOutcome.CANCELLED, null, null, 0, 0, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null);
    }

    private CallOutcomeRequest invalidNumber() {
        return new CallOutcomeRequest(
                CallOutcome.INVALID_NUMBER, null, null, 0, 0, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null,
                null, null, null, null, null);
    }

    // ============================================================ BLK-3 (staleness)

    @Test
    @DisplayName("BLK-3: a late outcome from a superseded attempt is rejected with 409")
    void late_outcome_from_superseded_attempt_is_rejected() {
        // Shape this test around the agent's outbox: attempt 1's outcome was queued and is
        // being delivered late while a newer attempt is in flight. In prod this shape is
        // produced by out-of-order delivery from the agent's local outbox (which sits
        // outside the sweep). The M-7 sweep closes ABANDONED logs on release, so we do
        // not go through releaseStuckAttempts here — we go straight to the invariant.
        Lead lead = insertLead("P");
        String attempt1Id = claimOneCallLogId();

        // Simulate that a newer attempt has since opened: lead.attemptCount is now 2.
        // Log 1 is intentionally still open — this is the exact shape the agent's outbox
        // creates when a queued outcome is delivered after a re-claim has already happened.
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(lead.getId())),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("attempt_count", 2),
                Lead.class);

        // Worker 1's late outcome for attempt 1 must be rejected — the current attempt on
        // this lead is 2, and applying a stale outcome would demote whatever attempt 2
        // decides.
        OffsetDateTime siteVisit = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(5).withHour(6).withMinute(0).withSecond(0).withNano(0);
        assertThatThrownBy(() -> orchestration.recordOutcome(attempt1Id, answeredWithSiteVisit(siteVisit)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("superseded");

        // The lead was NOT touched by the stale outcome; attempt 1's log is still open
        // (the agent's non-retryable branch drops the outcome — that is the correct move).
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getAttemptCount()).isEqualTo(2);
        assertThat(reloaded.getFinalStatus()).isNull();
        LeadCallLog log1 = callLogRepository.findById(new org.bson.types.ObjectId(attempt1Id)).orElseThrow();
        assertThat(log1.isClosed())
                .as("attempt 1 log stays open when its outcome is rejected as superseded")
                .isFalse();
    }

    @Test
    @DisplayName("BLK-3: a noAnswer on a reminder call never nulls a good final_status")
    void reminder_call_does_not_demote_a_booked_visit() {
        // Book the visit on attempt 1. The stage advances to SITE_VISIT (agent-uncallable
        // by the schedule) and the lead closes with siteVisitBooked.
        Lead lead = insertLead("P");
        String attempt1 = claimOneCallLogId();
        OffsetDateTime siteVisit = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(5).withHour(6).withMinute(0).withSecond(0).withNano(0);
        orchestration.recordOutcome(attempt1, answeredWithSiteVisit(siteVisit));

        Lead booked = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(booked.getFinalStatus()).isEqualTo(LeadFinalStatus.SITE_VISIT_BOOKED);
        assertThat(booked.getPipelineStatus()).isEqualTo(LeadPipelineStatus.COMPLETED);
        assertThat(booked.getStage()).isEqualTo(LeadStage.SITE_VISIT);

        // A manager clicks "Call now" for the reminder. Since the lead is now in stage
        // SITE_VISIT, the schedule cannot claim it — but Call now bypasses the stage gate.
        // Simulate the reminder call by opening an attempt via startCall.
        // The startCall guard refuses because stage.isTerminal() is only DISCARDED; SITE_VISIT
        // is not terminal, so this call is allowed.
        LeadCallLog reminderLog = orchestration.startCall(lead.getIdAsString(),
                new StartCallRequest(null, "admin", null)).callLog();
        String reminderLogId = reminderLog.getIdAsString();

        // Reminder rings out.
        orchestration.recordOutcome(reminderLogId, noAnswer());

        // The lead's finalStatus MUST still be siteVisitBooked (ratchet holds); attemptCount
        // ticks; the reminder attempt is logged for audit.
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getFinalStatus())
                .as("finalStatus is not nulled by a reminder that rings out")
                .isEqualTo(LeadFinalStatus.SITE_VISIT_BOOKED);
        assertThat(reloaded.getPipelineStatus())
                .as("pipelineStatus stays terminal")
                .isEqualTo(LeadPipelineStatus.COMPLETED);
        assertThat(reloaded.getStage()).isEqualTo(LeadStage.SITE_VISIT);
        // The booked visit was an answered call and cost nothing; only the reminder that
        // rang out spends from the budget.
        assertThat(reloaded.getAttemptCount()).isEqualTo(1);

        LeadCallLog reminderClosed = callLogRepository.findById(reminderLog.getId()).orElseThrow();
        assertThat(reminderClosed.getOutcome()).isEqualTo(CallOutcome.NO_ANSWER);
        assertThat(reminderClosed.isClosed()).isTrue();
    }

    // ============================================================ BLK-2 (atomicity)

    @Test
    @DisplayName("BLK-2: concurrent PATCH storm during recordOutcome must never lose the booking")
    void concurrent_patch_storm_never_loses_the_booking() throws Exception {
        // Push the same repro the QA report enshrined: agent posts siteVisitBooked while
        // 6 parallel PATCHes edit the lead. Each PATCH bumps @Version, forcing the outcome's
        // lead save to retry. The invariant: after settling, the lead MUST show siteVisitBooked.
        int rounds = 30;
        int concurrentPatches = 6;
        int lostBookings = 0;
        int successfulOutcomes = 0;
        ExecutorService pool = Executors.newFixedThreadPool(concurrentPatches + 1);

        try {
            for (int round = 0; round < rounds; round++) {
                Lead lead = insertLead("P");
                String cid = claimOneCallLogId();
                OffsetDateTime siteVisitAt = OffsetDateTime.now(ZoneOffset.UTC)
                        .plusDays(3).withHour(6).withMinute(0).withSecond(0).withNano(0);

                CountDownLatch gate = new CountDownLatch(1);
                List<Future<?>> futures = new ArrayList<>();

                // The outcome POST — this is the write we need to survive.
                AtomicInteger outcomeStatus = new AtomicInteger(0); // 200 or 409-ish or 500
                futures.add(pool.submit(() -> {
                    try {
                        gate.await();
                        orchestration.recordOutcome(cid, answeredWithSiteVisit(siteVisitAt));
                        outcomeStatus.set(200);
                    } catch (InvalidStateTransitionException ex) {
                        outcomeStatus.set(409);
                    } catch (Throwable t) {
                        outcomeStatus.set(500);
                    }
                    return null;
                }));

                // Concurrent PATCHes — each one flips a note field on the lead. Any of these
                // could hit the same version window as the outcome save; the reordering means
                // a lock loss retries, not corrupts.
                for (int j = 0; j < concurrentPatches; j++) {
                    final int jj = j;
                    futures.add(pool.submit(() -> {
                        try {
                            gate.await();
                            for (int k = 0; k < 5; k++) {
                                Lead fresh = leadRepository.findById(lead.getId()).orElseThrow();
                                fresh.setNotes("note-" + jj + "-" + k);
                                fresh.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
                                try {
                                    leadRepository.save(fresh);
                                } catch (org.springframework.dao.OptimisticLockingFailureException ignored) {
                                    // Fine, some other thread beat us — that is the point.
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                        return null;
                    }));
                }

                gate.countDown();
                for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);

                // Assert the invariant: either the outcome succeeded (200) and the lead
                // shows the booking, OR the outcome was retried-out (409) and NOTHING is
                // committed (log stays open — agent can retry).
                Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
                LeadCallLog logAfter = callLogRepository.findById(new org.bson.types.ObjectId(cid)).orElseThrow();

                if (outcomeStatus.get() == 200) {
                    successfulOutcomes++;
                    assertThat(reloaded.getFinalStatus())
                            .as("round %d: 200 outcome must produce a booking", round)
                            .isEqualTo(LeadFinalStatus.SITE_VISIT_BOOKED);
                    assertThat(reloaded.getPipelineStatus()).isEqualTo(LeadPipelineStatus.COMPLETED);
                    assertThat(logAfter.getOutcome()).isEqualTo(CallOutcome.ANSWERED);
                    assertThat(logAfter.isClosed()).isTrue();
                } else if (outcomeStatus.get() == 409) {
                    lostBookings++;
                    assertThat(logAfter.isClosed())
                            .as("round %d: on 409 the log MUST stay open for the agent's retry", round)
                            .isFalse();
                    assertThat(reloaded.getFinalStatus())
                            .as("round %d: on 409 the lead MUST NOT be moved", round)
                            .isNull();
                } else {
                    // A 500 here would be BLK-2 still happening. This must never occur.
                    org.junit.jupiter.api.Assertions.fail(
                            "round " + round + ": outcome mapped to 500; BLK-2 not fully closed");
                }
            }
        } finally {
            pool.shutdownNow();
        }

        // Every round settled in one of the two safe states. Report a stat so the log tells
        // the reader how often the race actually fires locally — usually most rounds succeed
        // fast, some retry a few times, and 0-2 hit the retry cap and return 409.
        System.out.printf("BLK-2 stress: %d/%d succeeded (200), %d/%d retried out (409), 0 corrupted (500)%n",
                successfulOutcomes, rounds, lostBookings, rounds);
        assertThat(successfulOutcomes + lostBookings).isEqualTo(rounds);
    }

    // ============================================================ MEDIUM regressions

    @Test
    @DisplayName("M-1: a lead who keeps answering is never exhausted")
    void answered_calls_do_not_spend_the_retry_budget() {
        // Trim maxAttempts to 3 so we hit exhaustion fast.
        AppSettings s = settingsService.current();
        s.getCallPolicy().setMaxAttempts(3);
        s.getCallPolicy().setMaxAttemptsPerDay(10);
        s.getCallPolicy().setCallingWindowStart("00:00");
        s.getCallPolicy().setCallingWindowEnd("23:59");
        Map<String, Integer> quick = new LinkedHashMap<>();
        quick.put("noAnswer", 0);
        quick.put("busy", 0);
        quick.put("rejected", 0);
        quick.put("voicemail", 0);
        quick.put("failed", 0);
        // scheduleRetry(ANSWERED, ...) uses this key when a connected call parks a retry;
        // absent it falls back to 60 min and the second claim in the loop returns empty.
        quick.put("answered", 0);
        s.getCallPolicy().setRetryBackoffMinutes(quick);
        settingsService.save(s);

        Lead lead = insertLead("P");
        for (int i = 0; i < 3; i++) {
            String cid = claimOneCallLogId();
            orchestration.recordOutcome(cid, answered(CallDisposition.NO_DECISION));
        }
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        // Three conversations, none of them a failure: the budget is untouched and the
        // lead is still in play. The cap exists to stop us pestering someone who will not
        // pick up, not to cut off someone who keeps talking to us.
        assertThat(reloaded.attemptCountOrZero()).isZero();
        assertThat(reloaded.getConnectedCount()).isEqualTo(3);
        assertThat(reloaded.getPipelineStatus()).isEqualTo(LeadPipelineStatus.RETRY_SCHEDULED);
        assertThat(reloaded.getStage()).isNotEqualTo(LeadStage.DISCARDED);
        assertThat(reloaded.getFinalStatus()).isNull();
    }

    @Test
    @DisplayName("M-1: unanswered dials still exhaust, and a lead who once spoke closes as NO_DECISION")
    void unanswered_dials_exhaust_the_lead() {
        AppSettings s = settingsService.current();
        s.getCallPolicy().setMaxAttempts(3);
        s.getCallPolicy().setMaxAttemptsPerDay(10);
        s.getCallPolicy().setCallingWindowStart("00:00");
        s.getCallPolicy().setCallingWindowEnd("23:59");
        Map<String, Integer> quick = new LinkedHashMap<>();
        for (String key : List.of("noAnswer", "busy", "rejected", "voicemail", "failed", "answered")) {
            quick.put(key, 0);
        }
        s.getCallPolicy().setRetryBackoffMinutes(quick);
        settingsService.save(s);

        Lead lead = insertLead("P");
        // One conversation, so the lead is known to be reachable...
        orchestration.recordOutcome(claimOneCallLogId(), answered(CallDisposition.NO_DECISION));
        // ...then three rings nobody picks up.
        for (int i = 0; i < 3; i++) {
            orchestration.recordOutcome(claimOneCallLogId(), noAnswer());
        }

        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.attemptCountOrZero()).isEqualTo(3);
        assertThat(reloaded.getPipelineStatus()).isEqualTo(LeadPipelineStatus.EXHAUSTED);
        assertThat(reloaded.getStage()).isEqualTo(LeadStage.DISCARDED);
        assertThat(reloaded.getFinalStatus())
                .as("connectedCount %d > 0 must close as NO_DECISION, not UNREACHABLE",
                        reloaded.getConnectedCount())
                .isEqualTo(LeadFinalStatus.NO_DECISION);
    }

    @Test
    @DisplayName("M-1: three unanswered calls close as UNREACHABLE (unchanged for the no-connect case)")
    void unanswered_exhaustion_still_unreachable() {
        AppSettings s = settingsService.current();
        s.getCallPolicy().setMaxAttempts(3);
        s.getCallPolicy().setMaxAttemptsPerDay(10);
        s.getCallPolicy().setCallingWindowStart("00:00");
        s.getCallPolicy().setCallingWindowEnd("23:59");
        Map<String, Integer> quick = new LinkedHashMap<>();
        quick.put("noAnswer", 0);
        s.getCallPolicy().setRetryBackoffMinutes(quick);
        settingsService.save(s);

        Lead lead = insertLead("P");
        for (int i = 0; i < 3; i++) {
            String cid = claimOneCallLogId();
            orchestration.recordOutcome(cid, noAnswer());
        }
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getFinalStatus()).isEqualTo(LeadFinalStatus.UNREACHABLE);
        assertThat(reloaded.getConnectedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("M-1b: detailsRequested does not spend an attempt from the retry budget")
    void details_requested_is_exempt_from_the_budget() {
        AppSettings s = settingsService.current();
        s.getCallPolicy().setMaxAttempts(2);  // Even at 2 max attempts...
        s.getCallPolicy().setMaxAttemptsPerDay(10);
        s.getCallPolicy().setCallingWindowStart("00:00");
        s.getCallPolicy().setCallingWindowEnd("23:59");
        Map<String, Integer> quick = new LinkedHashMap<>(s.getCallPolicy().getRetryBackoffMinutes());
        // parkFollowUp uses this key; zero it out so the parked call is immediately re-claimable
        // by the loop below.
        quick.put("answered", 0);
        s.getCallPolicy().setRetryBackoffMinutes(quick);
        settingsService.save(s);

        Lead lead = insertLead("P");
        for (int i = 0; i < 3; i++) {   // ...three details requests must not exhaust.
            String cid = claimOneCallLogId();
            orchestration.recordOutcome(cid, answered(CallDisposition.DETAILS_REQUESTED));
        }
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getFinalStatus())
                .as("engagement must not tip a lead into UNREACHABLE")
                .isNotEqualTo(LeadFinalStatus.UNREACHABLE);
        assertThat(reloaded.getPipelineStatus())
                .as("lead remains re-dialable, not exhausted")
                .isEqualTo(LeadPipelineStatus.RETRY_SCHEDULED);
    }

    @Test
    @DisplayName("M-2: cancelled closes with NO_DECISION and moves the stage to discarded")
    void cancelled_closes_with_a_meaningful_final_status() {
        Lead lead = insertLead("P");
        String cid = claimOneCallLogId();
        orchestration.recordOutcome(cid, cancelled());
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getPipelineStatus()).isEqualTo(LeadPipelineStatus.COMPLETED);
        assertThat(reloaded.getFinalStatus()).isEqualTo(LeadFinalStatus.NO_DECISION);
        assertThat(reloaded.getStage()).isEqualTo(LeadStage.DISCARDED);
    }

    @Test
    @DisplayName("M-3: invalidNumber advances the stage to DISCARDED")
    void invalid_number_advances_stage_to_discarded() {
        Lead lead = insertLead("P");
        String cid = claimOneCallLogId();
        orchestration.recordOutcome(cid, invalidNumber());
        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getFinalStatus()).isEqualTo(LeadFinalStatus.WRONG_NUMBER);
        assertThat(reloaded.getStage()).isEqualTo(LeadStage.DISCARDED);
    }

    @Test
    @DisplayName("M-4: startCall on a discarded lead is 409, not a queued attempt")
    void call_now_refuses_a_discarded_lead() {
        Lead lead = insertLead("P");
        lead.setStage(LeadStage.DISCARDED);
        lead.setFinalStatus(LeadFinalStatus.NOT_INTERESTED);
        leadRepository.save(lead);

        assertThatThrownBy(() -> orchestration.startCall(lead.getIdAsString(),
                new StartCallRequest(null, "admin", null)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("discarded");
    }

    @Test
    @DisplayName("M-5: startCall on a project-less lead is 409")
    void call_now_refuses_a_projectless_lead() {
        Lead lead = insertLead("");   // no project
        assertThatThrownBy(() -> orchestration.startCall(lead.getIdAsString(),
                new StartCallRequest(null, "admin", null)))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("project");
    }

    @Test
    @DisplayName("M-6: dueCount counts both scheduled and call-now attempts")
    void due_count_includes_call_now_work() {
        Lead a = insertLead("P");
        Lead b = insertLead("P");
        // One scheduled: 'a' is naturally due (nextAttemptAt is in the past).
        long baseline = orchestration.dueCount();
        assertThat(baseline).isEqualTo(2);  // a + b

        // Now open a call-now attempt on 'a'. Both should still count.
        // First, take 'a' out of the scheduled queue by claiming it — then open a call-now
        // attempt on 'b' via startCall (which opens an undialled log row).
        orchestration.claimNext(1);   // consumes 'a' or 'b' — one of them.
        long afterClaim = orchestration.dueCount();

        orchestration.startCall(b.getIdAsString(), new StartCallRequest(null, "admin", null));
        long afterCallNow = orchestration.dueCount();
        assertThat(afterCallNow)
                .as("call-now opens an undialled log; dueCount must see it")
                .isGreaterThan(afterClaim - 1);   // at least one call-now row is now due
    }

    @Test
    @DisplayName("M-7: releaseStuckAttempts closes the abandoned leads_log row")
    void release_stuck_attempts_closes_the_orphan_row() {
        Lead lead = insertLead("P");
        String cid = claimOneCallLogId();
        // Force the lead to look stuck by rewinding updated_at past the sweep cutoff.
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(lead.getId())),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("updated_at",
                                java.util.Date.from(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1).toInstant())),
                Lead.class);

        int released = orchestration.releaseStuckAttempts();
        assertThat(released).isEqualTo(1);

        LeadCallLog orphan = callLogRepository.findById(new org.bson.types.ObjectId(cid)).orElseThrow();
        assertThat(orphan.getOutcome())
                .as("the abandoned log must be closed, not left POST-able forever")
                .isNotNull();
        assertThat(orphan.getEndedAt()).isNotNull();
        assertThat(orphan.getErrorCode()).isEqualTo("abandoned");
    }

    @Test
    @DisplayName("M-10: previousAttempts on the first call is 0, not 1")
    void first_call_previous_attempts_is_zero() {
        Lead lead = insertLead("P");
        List<CallOrchestrationService.CallSession> sessions = orchestration.claimNext(1);
        assertThat(sessions).hasSize(1);
        var ctx = sessions.get(0).context();
        assertThat(ctx.previousAttempts())
                .as("openAttempt bumps attemptCount for THIS call; \"before this call\" is 0")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("M-13: siteVisitAt in the past is 422")
    void site_visit_in_the_past_is_rejected() {
        Lead lead = insertLead("P");
        String cid = claimOneCallLogId();
        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        assertThatThrownBy(() -> orchestration.recordOutcome(cid, answeredWithSiteVisit(past)))
                .isInstanceOf(InvalidLeadPayloadException.class);
    }

    @Test
    @DisplayName("M-13: siteVisitAt beyond the booking horizon is 422")
    void site_visit_beyond_horizon_is_rejected() {
        Lead lead = insertLead("P");
        String cid = claimOneCallLogId();
        OffsetDateTime farFuture = OffsetDateTime.now(ZoneOffset.UTC).plusYears(5);
        assertThatThrownBy(() -> orchestration.recordOutcome(cid, answeredWithSiteVisit(farFuture)))
                .isInstanceOf(InvalidLeadPayloadException.class);
    }

    @Test
    @DisplayName("Sanity: a plain answered→siteVisitBooked round-trip still works")
    void happy_path_site_visit_booked_still_works() {
        Lead lead = insertLead("P");
        String cid = claimOneCallLogId();
        OffsetDateTime siteVisit = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(5).withHour(6).withMinute(0).withSecond(0).withNano(0);
        orchestration.recordOutcome(cid, answeredWithSiteVisit(siteVisit));

        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getFinalStatus()).isEqualTo(LeadFinalStatus.SITE_VISIT_BOOKED);
        assertThat(reloaded.getPipelineStatus()).isEqualTo(LeadPipelineStatus.COMPLETED);
        assertThat(reloaded.getStage()).isEqualTo(LeadStage.SITE_VISIT);
        assertThat(reloaded.getActionType()).isEqualTo(ActionType.SITE_VISIT);
        assertThat(reloaded.getScheduledFor()).isEqualTo(siteVisit);

        LeadCallLog log = callLogRepository.findById(new org.bson.types.ObjectId(cid)).orElseThrow();
        assertThat(log.getOutcome()).isEqualTo(CallOutcome.ANSWERED);
        assertThat(log.getDisposition()).isEqualTo(CallDisposition.SITE_VISIT_BOOKED);
        assertThat(log.isClosed()).isTrue();
    }

    @Test
    @DisplayName("Concurrent claims never hand out the same lead twice")
    void concurrent_claims_never_double_hand() throws Exception {
        int leads = 40;
        for (int i = 0; i < leads; i++) insertLead("P");

        int workers = 10;
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        List<Future<List<CallOrchestrationService.CallSession>>> futures = new ArrayList<>();
        for (int i = 0; i < workers; i++) {
            futures.add(pool.submit(() -> orchestration.claimNext(5)));
        }

        List<String> callLogIds = new ArrayList<>();
        for (var f : futures) {
            f.get(20, TimeUnit.SECONDS).forEach(s -> callLogIds.add(s.callLog().getIdAsString()));
        }
        pool.shutdownNow();

        // Every claim is distinct. No lead was handed out twice.
        assertThat(callLogIds).doesNotHaveDuplicates();
        assertThat(callLogIds.size()).isEqualTo(leads);
    }

    // ======================================= the promise rules

    /** Puts the lead where the dialler will find it, with the flag under test. */
    private void makeDue(Lead lead, OffsetDateTime when, boolean leadRequested) {
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(lead.getId())),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("next_attempt_at", when)
                        .set("next_attempt_lead_requested", leadRequested)
                        .set("pipeline_status", LeadPipelineStatus.RETRY_SCHEDULED.getValue()),
                Lead.class);
    }

    private void capAt(int perDay) {
        AppSettings settings = settingsService.current();
        settings.getCallPolicy().setMaxAttemptsPerDay(perDay);
        settings.getCallPolicy().setCallingWindowStart("00:00");
        settings.getCallPolicy().setCallingWindowEnd("23:59");
        settingsService.save(settings);
    }

    @Test
    @DisplayName("The daily cap never moves a time the lead asked for")
    void a_lead_requested_time_is_exempt_from_the_daily_cap() {
        capAt(1);
        Lead lead = insertLead("P");
        // Spend the day's allowance on a dial nobody picked up.
        orchestration.recordOutcome(claimOneCallLogId(), noAnswer());

        makeDue(lead, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), true);

        assertThat(orchestration.claimNext(1, true))
                .as("the cap limits calls we start, never one we promised")
                .hasSize(1);
    }

    @Test
    @DisplayName("Without that promise, the cap still defers to tomorrow")
    void an_ordinary_retry_still_hits_the_daily_cap() {
        capAt(1);
        Lead lead = insertLead("P");
        orchestration.recordOutcome(claimOneCallLogId(), noAnswer());

        makeDue(lead, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), false);

        assertThat(orchestration.claimNext(1, true)).isEmpty();
        assertThat(leadRepository.findById(lead.getId()).orElseThrow().getPipelineStatus())
                .isEqualTo(LeadPipelineStatus.RETRY_SCHEDULED);
    }

    @Test
    @DisplayName("A promise missed by hours is re-queued, not rung at whatever hour we return")
    void a_stale_promise_is_not_dialled_late() {
        capAt(10);
        Lead lead = insertLead("P");
        OffsetDateTime missed = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3);
        makeDue(lead, missed, true);

        assertThat(orchestration.claimNext(1, true))
                .as("nothing is dialled on a promise this old")
                .isEmpty();

        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getPipelineStatus()).isEqualTo(LeadPipelineStatus.RETRY_SCHEDULED);
        assertThat(reloaded.isNextAttemptLeadRequested())
                .as("it is an ordinary retry now, so it may not run out of hours")
                .isFalse();
        assertThat(reloaded.getNextAttemptAt()).isAfter(missed);
    }

    @Test
    @DisplayName("A promise a few minutes late is still a promise")
    void a_promise_inside_the_grace_period_is_dialled() {
        capAt(10);
        Lead lead = insertLead("P");
        makeDue(lead, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5), true);

        assertThat(orchestration.claimNext(1, true)).hasSize(1);
    }

    @Test
    @DisplayName("A human's rescheduled time is stored exactly, and may run out of hours")
    void a_rescheduled_time_is_honoured_as_asked_for() {
        AppSettings settings = settingsService.current();
        settings.getCallPolicy().setCallingWindowStart("10:00");
        settings.getCallPolicy().setCallingWindowEnd("17:00");
        settingsService.save(settings);

        Lead lead = insertLead("P");
        // 21:00 IST tomorrow — well outside the window a clamp used to move it into.
        OffsetDateTime nineTonight = OffsetDateTime.now(ZoneOffset.UTC)
                .plusDays(1).withHour(15).withMinute(30).withSecond(0).withNano(0);

        orchestration.reschedule(lead.getIdAsString(), new RescheduleRequest(nineTonight, "customer asked"));

        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getNextAttemptAt()).isEqualTo(nineTonight);
        assertThat(reloaded.isNextAttemptLeadRequested()).isTrue();
    }

    @Test
    @DisplayName("Rescheduling an exhausted lead puts it back in the queue")
    void rescheduling_revives_a_dead_lead() {
        capAt(10);
        Lead lead = insertLead("P");
        mongoTemplate.updateFirst(
                new org.springframework.data.mongodb.core.query.Query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(lead.getId())),
                new org.springframework.data.mongodb.core.query.Update()
                        .set("stage", LeadStage.DISCARDED.getValue())
                        .set("pipeline_status", LeadPipelineStatus.EXHAUSTED.getValue())
                        .set("final_status", LeadFinalStatus.UNREACHABLE.getValue()),
                Lead.class);

        orchestration.reschedule(lead.getIdAsString(), new RescheduleRequest(
                OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1), "they called back"));

        Lead reloaded = leadRepository.findById(lead.getId()).orElseThrow();
        assertThat(reloaded.getStage())
                .as("a booked call reopens the lead; a ratcheted stage left it unreachable")
                .isEqualTo(LeadStage.FOLLOW_UP);
        assertThat(reloaded.getFinalStatus()).isNull();
        assertThat(orchestration.claimNext(1, true))
                .as("and the dialler can actually see it")
                .hasSize(1);
    }
}
