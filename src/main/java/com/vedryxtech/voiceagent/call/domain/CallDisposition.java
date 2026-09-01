package com.vedryxtech.voiceagent.call.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** What the lead actually agreed to on a connected call. Drives the next pipeline move. */
public enum CallDisposition implements WireValue {

    SITE_VISIT_BOOKED("siteVisitBooked"),
    CALLBACK_REQUESTED("callbackRequested"),
    RESCHEDULED("rescheduled"),
    DETAILS_REQUESTED("detailsRequested"),
    INTERESTED("interested"),
    NOT_INTERESTED("notInterested"),
    DO_NOT_CALL("doNotCall"),
    WRONG_NUMBER("wrongNumber"),
    LANGUAGE_BARRIER("languageBarrier"),
    UNQUALIFIED("unqualified"),
    NO_DECISION("noDecision");

    private final String value;

    CallDisposition(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CallDisposition fromValue(String raw) {
        return WireValues.parse(CallDisposition.class, raw);
    }

    /** Dispositions that book another conversation rather than closing the lead. */
    public boolean requiresFollowUp() {
        return this == CALLBACK_REQUESTED || this == RESCHEDULED || this == LANGUAGE_BARRIER;
    }

    /** Dispositions that close the lead for good. */
    public boolean isFinal() {
        return this == SITE_VISIT_BOOKED || this == NOT_INTERESTED || this == DO_NOT_CALL
                || this == WRONG_NUMBER || this == UNQUALIFIED;
    }
}
