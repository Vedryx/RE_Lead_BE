package com.vedryxtech.voiceagent.lead.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle of a single lead action. */
public enum LeadStatus implements WireValue {

    REQUESTED("requested"),
    SCHEDULED("scheduled"),
    RESCHEDULED("rescheduled"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    NO_SHOW("noShow"),
    FAILED("failed");

    private final String value;

    LeadStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static LeadStatus fromValue(String raw) {
        return WireValues.parse(LeadStatus.class, raw);
    }
}
