package com.vedryxtech.voiceagent.lead.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a lead sits in the dialling pipeline. This is the field the dashboard counts
 * ("how many are done, how many are still pending") and the field the dialler mutates.
 */
public enum LeadPipelineStatus implements WireValue {

    /** Imported, not yet queued for a call. */
    NEW("new"),
    /** Eligible to be picked up by the dialler now. */
    QUEUED("queued"),
    /** Claimed by a worker, call being placed. */
    DIALING("dialing"),
    /** Answered and currently talking. */
    IN_PROGRESS("inProgress"),
    /** Not reached; another attempt is booked at next_attempt_at. */
    RETRY_SCHEDULED("retryScheduled"),
    /** The lead asked to be called at a specific later time. */
    CALLBACK_SCHEDULED("callbackScheduled"),
    /** Conversation finished with a final outcome. */
    COMPLETED("completed"),
    /** Retry budget spent without ever connecting. */
    EXHAUSTED("exhausted"),
    /** Do-not-call or otherwise blocked from dialling. */
    SUPPRESSED("suppressed"),
    /** Technical failure that needs a human to look at it. */
    FAILED("failed");

    private final String value;

    LeadPipelineStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LeadPipelineStatus fromValue(String raw) {
        return WireValues.parse(LeadPipelineStatus.class, raw);
    }

    /** Terminal states are never re-dialled. */
    public boolean isTerminal() {
        return this == COMPLETED || this == EXHAUSTED || this == SUPPRESSED;
    }

    /** Everything still owing a call: what the dashboard shows as "pending". */
    public boolean isPending() {
        return this == NEW || this == QUEUED || this == RETRY_SCHEDULED || this == CALLBACK_SCHEDULED;
    }

    /** Currently occupying a dialler slot. */
    public boolean isActive() {
        return this == DIALING || this == IN_PROGRESS;
    }
}
