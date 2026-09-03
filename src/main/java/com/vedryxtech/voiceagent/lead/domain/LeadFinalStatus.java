package com.vedryxtech.voiceagent.lead.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** The one-line answer to "what happened to this lead". Set only once the lead is closed. */
public enum LeadFinalStatus implements WireValue {

    SITE_VISIT_BOOKED("siteVisitBooked"),
    SITE_VISIT_DONE("siteVisitDone"),
    INTERESTED("interested"),
    NOT_INTERESTED("notInterested"),
    /** Talked but never committed. Distinct from UNREACHABLE, which means we never spoke. */
    NO_DECISION("noDecision"),
    UNREACHABLE("unreachable"),
    DO_NOT_CALL("doNotCall"),
    WRONG_NUMBER("wrongNumber"),
    UNQUALIFIED("unqualified"),
    DUPLICATE("duplicate");

    private final String value;

    LeadFinalStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LeadFinalStatus fromValue(String raw) {
        return WireValues.parse(LeadFinalStatus.class, raw);
    }

    /** Counts towards conversion on the dashboard. */
    public boolean isWon() {
        return this == SITE_VISIT_BOOKED || this == SITE_VISIT_DONE;
    }
}
