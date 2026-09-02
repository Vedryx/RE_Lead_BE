package com.vedryxtech.voiceagent.lead.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How far along a lead is, in the terms a salesperson thinks in.
 *
 * <p>Distinct from the two statuses that already exist. {@code pipelineStatus} is machine
 * state and bounces on every retry; {@code finalStatus} is null for every lead still in
 * play. Neither answers "how far along is this person?".
 *
 * <p>Derived, never independently written: {@code recordOutcome} sets it in the same
 * transition that sets the other two, so it cannot drift. The agent never sends it.
 *
 * <p>{@link #DISCARDED} is deliberately one stage rather than four. Why a lead was
 * discarded is already in {@code finalStatus} — notInterested, unqualified, unreachable,
 * doNotCall, wrongNumber, duplicate. The stage answers where in the funnel; the existing
 * field answers why.
 */
public enum LeadStage implements WireValue {

    /** Never reached, or reached and nothing agreed. */
    NEW("new", 0),
    /** We owe them another call — busy, deferred, or waiting on WhatsApp details. */
    FOLLOW_UP("followUp", 1),
    /** A person owes them a call. The agent must not dial these. */
    CALLBACK_REQUESTED("callbackRequested", 2),
    /** Visit booked. Reached by the agent only through "Call now", for a reminder. */
    SITE_VISIT("siteVisit", 3),
    /** Out of the funnel, for any reason. See finalStatus for which. */
    DISCARDED("discarded", -1);

    /** Rank of a terminal stage; ranks otherwise increase along the funnel. */
    private static final int TERMINAL_RANK = -1;

    private final String value;
    private final int rank;

    LeadStage(String value, int rank) {
        this.value = value;
        this.rank = rank;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LeadStage fromValue(String raw) {
        return WireValues.parse(LeadStage.class, raw);
    }

    public boolean isTerminal() {
        return rank == TERMINAL_RANK;
    }

    /** Stages the agent is allowed to dial. Nothing else, whatever the schedule says. */
    public boolean isAgentCallable() {
        return this == NEW || this == FOLLOW_UP;
    }

    /**
     * Apply an agent-driven transition. Stages ratchet: forward, or out, never backwards.
     *
     * <p>Without this a missed reminder call on a booked visit would drag someone from
     * SITE_VISIT back to NEW, and the funnel would quietly under-report every lead it had
     * already won.
     *
     * <p>Only for outcomes the agent reports. A human PATCH bypasses this on purpose —
     * someone discarded by mistake has to be recoverable without a database edit.
     */
    public LeadStage advanceTo(LeadStage next) {
        if (next == null) {
            return this;
        }
        if (next == DISCARDED) {
            return next;            // an exit always applies, from anywhere
        }
        if (this.isTerminal()) {
            return this;            // discarded stays discarded
        }
        return next.rank > this.rank ? next : this;
    }
}
