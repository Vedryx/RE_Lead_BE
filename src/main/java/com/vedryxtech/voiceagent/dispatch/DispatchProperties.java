package com.vedryxtech.voiceagent.dispatch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the scheduler needs in order to place a call.
 *
 * <p>Disabled by default. A backend that starts dialling the moment it boots is not
 * something to switch on by accident, and every environment that is not production
 * wants the queue read but never acted on.
 */
@ConfigurationProperties(prefix = "app.dispatch")
public class DispatchProperties {

    /** Nothing is dialled while this is false. The queue still fills; it is just not drained. */
    private boolean enabled = false;

    /**
     * How many calls may be in progress at once.
     *
     * <p>Three is comfortable. LiveKit's Build plan refuses a sixth concurrent agent
     * session, so five is the hard ceiling above this one.
     */
    private int maxConcurrent = 3;

    /** How often to look for work. */
    private long pollSeconds = 15;

    /** {@code wss://…} — the same project the agent is deployed in. */
    private String livekitUrl = "";

    /** Which agent the dispatch is addressed to; must match the worker's registered name. */
    private String agentName = "the-real-estate";

    /** The SIP trunk that reaches the PSTN. */
    private String outboundTrunkId = "";

    /** The number the lead sees. */
    private String callerId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    public void setMaxConcurrent(int maxConcurrent) {
        this.maxConcurrent = maxConcurrent;
    }

    public long getPollSeconds() {
        return pollSeconds;
    }

    public void setPollSeconds(long pollSeconds) {
        this.pollSeconds = pollSeconds;
    }

    public String getLivekitUrl() {
        return livekitUrl;
    }

    public void setLivekitUrl(String livekitUrl) {
        this.livekitUrl = livekitUrl;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getOutboundTrunkId() {
        return outboundTrunkId;
    }

    public void setOutboundTrunkId(String outboundTrunkId) {
        this.outboundTrunkId = outboundTrunkId;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }
}
