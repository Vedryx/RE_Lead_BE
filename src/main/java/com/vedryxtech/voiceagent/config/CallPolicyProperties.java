package com.vedryxtech.voiceagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.dialer.*}. Runtime knobs the dialler sweep uses. The retry rules the
 * agent reads live on the {@code app_settings} singleton, not here.
 */
@ConfigurationProperties(prefix = "app.dialer")
public class CallPolicyProperties {

    /** Poll the queue and log what is due. Actual dialling is driven by the voice agent worker. */
    private boolean schedulerEnabled = false;

    /** How many leads one claim call may take. */
    private int batchSize = 25;

    /** Attempts older than this with no outcome are treated as stuck and released. */
    private int staleDialMinutes = 15;

    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    public void setSchedulerEnabled(boolean schedulerEnabled) {
        this.schedulerEnabled = schedulerEnabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getStaleDialMinutes() {
        return staleDialMinutes;
    }

    public void setStaleDialMinutes(int staleDialMinutes) {
        this.staleDialMinutes = staleDialMinutes;
    }
}
