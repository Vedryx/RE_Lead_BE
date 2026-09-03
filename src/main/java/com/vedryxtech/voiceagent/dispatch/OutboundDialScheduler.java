package com.vedryxtech.voiceagent.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vedryxtech.voiceagent.call.api.dto.CallContext;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Places the calls the queue says are due.
 *
 * <p>This used to be a separate Python process polling the same endpoints from
 * outside. It lived in the agent container so LiveKit would host it, until the
 * obvious problem surfaced: LiveKit scales an idle agent to zero, and an agent that
 * is asleep cannot dispatch the call that would wake it. The dialler had to live
 * somewhere always on, and the service that already owns the queue is the shortest
 * answer — no extra deployable, no second place for the concurrency ceiling to be
 * evaluated wrongly.
 *
 * <p>Claiming is a commitment: it marks the lead {@code DIALING} and spends one of its
 * attempts. So this claims exactly as many as it can dial and not one more.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.dispatch.enabled", havingValue = "true")
public class OutboundDialScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboundDialScheduler.class);

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final CallOrchestrationService orchestration;
    private final SettingsService settings;
    private final LiveKitClient livekit;
    private final DispatchProperties properties;
    private final ObjectMapper objectMapper;

    public OutboundDialScheduler(CallOrchestrationService orchestration,
                                 SettingsService settings,
                                 LiveKitClient livekit,
                                 DispatchProperties properties,
                                 ObjectMapper objectMapper) {
        this.orchestration = orchestration;
        this.settings = settings;
        this.livekit = livekit;
        this.properties = properties;
        this.objectMapper = objectMapper;
        log.info("Outbound dialling is on: at most {} concurrent, polling every {}s",
                properties.getMaxConcurrent(), properties.getPollSeconds());
    }

    @Scheduled(fixedDelayString = "${app.dispatch.poll-seconds:15}000",
            initialDelayString = "${app.dispatch.initial-delay-ms:20000}")
    public void dialWhatIsDue() {
        try {
            placeCalls();
        } catch (RuntimeException ex) {
            // A failed pass must not kill the scheduler thread — the next tick should
            // still run. Anything already claimed is freed by the stale-dial sweep.
            log.error("Dialling pass failed: {}", ex.getMessage(), ex);
        }
    }

    private void placeCalls() {
        CallPolicy policy = settings.current().getCallPolicy();

        if (!withinCallingWindow(policy)) {
            return;
        }

        // Deliberately not gated on dueCount. That counts leads in a claimable pipeline
        // status, and "Call now" moves a lead straight to DIALING — so a manual call
        // reads as zero due and would be skipped, even though claimNext is exactly what
        // picks its undialled attempt up.
        int live = livekit.liveCallCount();
        int free = properties.getMaxConcurrent() - live;
        if (free <= 0) {
            log.info("{} call(s) already in progress at a ceiling of {} — waiting",
                    live, properties.getMaxConcurrent());
            return;
        }

        List<CallOrchestrationService.CallSession> claimed = orchestration.claimNext(free);
        if (claimed.isEmpty()) {
            return;
        }
        for (CallOrchestrationService.CallSession session : claimed) {
            try {
                dial(session, policy);
            } catch (RuntimeException ex) {
                // The lead is claimed and now stranded. Say so plainly: the sweep is the
                // only thing that frees it, and that takes fifteen minutes.
                log.error("Could not dial claimed lead {} — stranded until the sweep frees it: {}",
                        session.lead().getIdAsString(), ex.getMessage());
            }
        }
    }

    private void dial(CallOrchestrationService.CallSession session, CallPolicy policy) {
        String phone = session.lead().getCallingPhone();
        if (phone == null || phone.isBlank()) {
            log.error("Lead {} has no number to call", session.lead().getIdAsString());
            return;
        }

        String purpose = purposeFor(session.context());
        String room = LiveKitClient.ROOM_PREFIX + purpose + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String metadata = metadataFor(session, purpose, policy);

        // Order matters: the agent is dispatched first so it is in the room before the
        // lead answers. Ringing into an empty room is how a lead gets silence.
        livekit.dispatchAgent(room, metadata);
        livekit.dialOut(room, phone, session.lead().getName(), metadata);

        log.info("Dialling {} {} in {} (callLogId={})",
                session.lead().getName(), phone, room, session.callLog().getIdAsString());
    }

    /**
     * What kind of call this is, from what the CRM already knows.
     *
     * <p>Opening with the first-call introduction to someone who has been rung twice is
     * what makes an agent sound like a machine that forgot.
     */
    private static String purposeFor(CallContext context) {
        if (context == null) {
            return "initial_lead";
        }
        String pending = context.pendingAction() == null ? "" : context.pendingAction().getValue();
        if ("followUpCall".equals(pending)) {
            return "follow_up";
        }
        if ("siteVisit".equals(pending)) {
            return "site_visit_reminder";
        }
        if (context.previousAttempts() > 1 && context.previousConnects() == 0) {
            return "repeat_attempt";
        }
        return "initial_lead";
    }

    /**
     * Everything the agent needs, sent with the call.
     *
     * <p>The call process is short-lived and cannot ask questions once the room exists,
     * so anything it needs has to travel with the dispatch — including the calling
     * rules, which would otherwise be an HTTP round trip before the lead answers.
     */
    private String metadataFor(CallOrchestrationService.CallSession session, String purpose,
                               CallPolicy policy) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", session.lead().getName());
        metadata.put("phone", session.lead().getCallingPhone());
        metadata.put("call_purpose", purpose);
        metadata.put("lead_id", session.lead().getIdAsString());
        metadata.put("crm_lead_id", session.lead().getIdAsString());
        metadata.put("call_log_id", session.callLog().getIdAsString());
        metadata.put("recording_key", session.callLog().getRecordingKey());
        metadata.put("site_visit_datetime", session.lead().getScheduledFor());
        metadata.put("context", session.context());
        metadata.put("call_policy", policy);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build call metadata", ex);
        }
    }

    /**
     * Nobody is rung outside the calling window.
     *
     * <p>The Python dialler never checked: it trusted {@code nextAttemptAt}, which is
     * clamped when a retry is scheduled but not when a lead is created. A lead added at
     * two in the morning was due immediately and would have been called.
     */
    private boolean withinCallingWindow(CallPolicy policy) {
        LocalTime now = ZonedDateTime.now(IST).toLocalTime();
        LocalTime open = policy.windowStart();
        LocalTime close = policy.windowEnd();
        if (now.isBefore(open) || now.isAfter(close)) {
            log.debug("Outside the calling window {}–{}; not dialling at {}", open, close, now);
            return false;
        }
        return true;
    }
}
