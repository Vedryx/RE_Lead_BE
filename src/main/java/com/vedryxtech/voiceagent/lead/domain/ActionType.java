package com.vedryxtech.voiceagent.lead.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The action the voice agent committed to on behalf of the lead.
 * Serialized to and from camelCase wire values; snake_case input is still accepted.
 */
public enum ActionType implements WireValue {

    TEAM_CALLBACK("teamCallback"),
    SITE_VISIT("siteVisit"),
    FOLLOW_UP_CALL("followUpCall"),
    WHATSAPP_PROJECT_DETAILS("whatsappProjectDetails");

    private final String value;

    ActionType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ActionType fromValue(String raw) {
        return WireValues.parse(ActionType.class, raw);
    }

    /** Actions that occupy a slot in the calendar and therefore need {@code scheduledFor}. */
    public boolean isCalendarBooking() {
        return this == SITE_VISIT || this == FOLLOW_UP_CALL;
    }
}
