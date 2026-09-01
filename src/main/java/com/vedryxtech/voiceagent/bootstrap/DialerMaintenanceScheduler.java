package com.vedryxtech.voiceagent.bootstrap;

import com.vedryxtech.voiceagent.config.CallPolicyProperties;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Housekeeping the dialler cannot do for itself.
 *
 * <p>A worker that dies mid-call leaves its lead stuck in {@code dialing}, where nothing would
 * ever claim it again. This sweeps those back into the queue, and logs the depth of the queue
 * so a backlog is visible before anyone complains about it.</p>
 *
 * <p>Placing the calls is not this service's job - that belongs to the voice-agent worker,
 * which pulls work from {@code POST /api/v1/call-queue/claims}.</p>
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.dialer.scheduler-enabled", havingValue = "true")
public class DialerMaintenanceScheduler {

    private static final Logger log = LoggerFactory.getLogger(DialerMaintenanceScheduler.class);

    private final CallOrchestrationService orchestrationService;
    private final CallPolicyProperties properties;

    public DialerMaintenanceScheduler(CallOrchestrationService orchestrationService,
                                      CallPolicyProperties properties) {
        this.orchestrationService = orchestrationService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.dialer.maintenance-interval-ms:60000}",
            initialDelayString = "${app.dialer.maintenance-initial-delay-ms:30000}")
    public void sweep() {
        try {
            int released = orchestrationService.releaseStuckAttempts();
            long due = orchestrationService.dueCount();
            if (released > 0 || due > 0) {
                log.info("{} lead(s) due, {} released after {} min stuck in dialing",
                        due, released, properties.getStaleDialMinutes());
            }
        } catch (RuntimeException ex) {
            // A failed sweep must not kill the scheduler thread.
            log.error("Maintenance sweep failed: {}", ex.getMessage());
        }
    }
}
