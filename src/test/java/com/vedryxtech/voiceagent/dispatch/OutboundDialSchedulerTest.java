package com.vedryxtech.voiceagent.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import com.vedryxtech.voiceagent.call.api.dto.CallContext;
import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Claiming is a commitment: it marks the lead dialing and spends one of its four
 * attempts. Everything here is about not claiming more than can actually be dialled.
 */
class OutboundDialSchedulerTest {

    private CallOrchestrationService orchestration;
    private LiveKitClient livekit;
    private DispatchProperties properties;
    private OutboundDialScheduler scheduler;

    @BeforeEach
    void setUp() {
        orchestration = mock(CallOrchestrationService.class);
        livekit = mock(LiveKitClient.class);

        properties = new DispatchProperties();
        properties.setEnabled(true);
        properties.setMaxConcurrent(3);
        properties.setOutboundTrunkId("ST_test");
        properties.setCallerId("+911234567890");
        properties.setLivekitUrl("wss://test.livekit.cloud");

        SettingsService settings = mock(SettingsService.class);
        AppSettings appSettings = new AppSettings();
        CallPolicy policy = CallPolicy.defaults();
        // Wide open, so tests are not hostage to the hour they run at.
        policy.setCallingWindowStart("00:00");
        policy.setCallingWindowEnd("23:59");
        appSettings.setCallPolicy(policy);
        when(settings.current()).thenReturn(appSettings);

        scheduler = new OutboundDialScheduler(orchestration, settings, livekit, properties,
                new ObjectMapper());
    }

    @Test
    void an_empty_queue_claims_nothing() {
        when(orchestration.dueCount()).thenReturn(0L);

        scheduler.dialWhatIsDue();

        verify(orchestration, never()).claimNext(anyInt());
        verify(livekit, never()).liveCallCount();
    }

    @Test
    void it_claims_only_as_many_as_there_are_free_slots() {
        // Ten due, two already in progress, ceiling of three: exactly one may be taken.
        // Claiming the other nine would strand them in dialing for fifteen minutes.
        when(orchestration.dueCount()).thenReturn(10L);
        when(livekit.liveCallCount()).thenReturn(2);
        when(orchestration.claimNext(1)).thenReturn(List.of(session("+919000000001")));

        scheduler.dialWhatIsDue();

        verify(orchestration).claimNext(1);
    }

    @Test
    void it_never_claims_more_than_are_due() {
        when(orchestration.dueCount()).thenReturn(1L);
        when(livekit.liveCallCount()).thenReturn(0);
        when(orchestration.claimNext(1)).thenReturn(List.of(session("+919000000001")));

        scheduler.dialWhatIsDue();

        verify(orchestration).claimNext(1);
    }

    @Test
    void at_the_ceiling_it_claims_nothing_at_all() {
        when(orchestration.dueCount()).thenReturn(5L);
        when(livekit.liveCallCount()).thenReturn(3);

        scheduler.dialWhatIsDue();

        verify(orchestration, never()).claimNext(anyInt());
    }

    @Test
    void the_agent_is_dispatched_before_the_phone_rings() {
        // Ringing into a room the agent has not joined is how a lead gets silence.
        when(orchestration.dueCount()).thenReturn(1L);
        when(livekit.liveCallCount()).thenReturn(0);
        when(orchestration.claimNext(anyInt())).thenReturn(List.of(session("+919000000001")));

        scheduler.dialWhatIsDue();

        var order = org.mockito.Mockito.inOrder(livekit);
        order.verify(livekit).dispatchAgent(anyString(), anyString());
        order.verify(livekit).dialOut(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void one_lead_that_cannot_be_dialled_does_not_stop_the_batch() {
        when(orchestration.dueCount()).thenReturn(2L);
        when(livekit.liveCallCount()).thenReturn(0);
        when(orchestration.claimNext(anyInt()))
                .thenReturn(List.of(session("+919000000001"), session("+919000000002")));
        org.mockito.Mockito.doThrow(new RuntimeException("trunk refused"))
                .doNothing()
                .when(livekit).dialOut(anyString(), anyString(), anyString(), anyString());

        scheduler.dialWhatIsDue();

        verify(livekit, times(2)).dialOut(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void a_lead_with_no_number_is_skipped_rather_than_dialled() {
        when(orchestration.dueCount()).thenReturn(1L);
        when(livekit.liveCallCount()).thenReturn(0);
        when(orchestration.claimNext(anyInt())).thenReturn(List.of(session(null)));

        scheduler.dialWhatIsDue();

        verify(livekit, never()).dialOut(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void nobody_is_rung_outside_the_calling_window() {
        // The Python dialler never checked this: it trusted nextAttemptAt, which is
        // clamped on retry but not on creation. A lead added at 2am was due at once.
        SettingsService settings = mock(SettingsService.class);
        AppSettings appSettings = new AppSettings();
        CallPolicy closed = CallPolicy.defaults();
        closed.setCallingWindowStart("03:00");
        closed.setCallingWindowEnd("03:01");
        appSettings.setCallPolicy(closed);
        when(settings.current()).thenReturn(appSettings);
        var nightScheduler = new OutboundDialScheduler(orchestration, settings, livekit,
                properties, new ObjectMapper());

        nightScheduler.dialWhatIsDue();

        verify(orchestration, never()).dueCount();
        verify(orchestration, never()).claimNext(anyInt());
    }

    @Test
    void a_failed_pass_never_kills_the_scheduler_thread() {
        when(orchestration.dueCount()).thenThrow(new RuntimeException("mongo is away"));

        assertThatCode(() -> scheduler.dialWhatIsDue()).doesNotThrowAnyException();
    }

    // --- which kind of call is this? Moved here with the dialler. ---

    @Test
    void a_pending_follow_up_opens_as_a_follow_up() {
        assertThat(dispatchedMetadataFor(context(ActionType.FOLLOW_UP_CALL, 0, 0)))
                .contains("\"call_purpose\":\"follow_up\"");
    }

    @Test
    void a_booked_visit_opens_as_a_reminder() {
        assertThat(dispatchedMetadataFor(context(ActionType.SITE_VISIT, 0, 0)))
                .contains("\"call_purpose\":\"site_visit_reminder\"");
    }

    @Test
    void tried_before_and_never_answered_opens_as_a_repeat() {
        // Replaying the first-call introduction to someone rung twice already is what
        // makes an agent sound like a machine that forgot.
        assertThat(dispatchedMetadataFor(context(null, 3, 0)))
                .contains("\"call_purpose\":\"repeat_attempt\"");
    }

    @Test
    void someone_who_has_answered_before_gets_the_ordinary_opening() {
        assertThat(dispatchedMetadataFor(context(null, 3, 1)))
                .contains("\"call_purpose\":\"initial_lead\"");
    }

    @Test
    void a_first_call_with_no_history_opens_as_a_first_call() {
        assertThat(dispatchedMetadataFor(null))
                .contains("\"call_purpose\":\"initial_lead\"");
    }

    private static CallContext context(ActionType pending, int attempts, int connects) {
        return new CallContext("My Home Sanctuary", LeadStage.NEW, pending, null, null, null,
                null, attempts, connects, List.of());
    }

    /** Runs one pass and returns the metadata the agent would have been handed. */
    private String dispatchedMetadataFor(CallContext context) {
        when(orchestration.dueCount()).thenReturn(1L);
        when(livekit.liveCallCount()).thenReturn(0);
        Lead lead = new Lead();
        lead.setId(new ObjectId());
        lead.setName("Test Lead");
        lead.setCallingPhone("+919000000001");
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId());
        when(orchestration.claimNext(anyInt())).thenReturn(List.of(
                new CallOrchestrationService.CallSession(lead, callLog, true, context)));

        scheduler.dialWhatIsDue();

        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(livekit).dispatchAgent(anyString(), captured.capture());
        return captured.getValue();
    }

    private static CallOrchestrationService.CallSession session(String phone) {
        Lead lead = new Lead();
        lead.setId(new ObjectId());
        lead.setName("Test Lead");
        lead.setCallingPhone(phone);
        lead.setProject("My Home Sanctuary");
        LeadCallLog callLog = new LeadCallLog();
        callLog.setId(new ObjectId());
        callLog.setRecordingKey("recordings/p/2026/09/x/audio.ogg");
        return new CallOrchestrationService.CallSession(lead, callLog, true);
    }
}
