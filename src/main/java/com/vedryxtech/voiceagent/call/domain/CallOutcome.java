package com.vedryxtech.voiceagent.call.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** How the telephony leg itself ended. Independent of what was agreed on the call. */
public enum CallOutcome implements WireValue {

    ANSWERED("answered"),
    NO_ANSWER("noAnswer"),
    BUSY("busy"),
    REJECTED("rejected"),
    VOICEMAIL("voicemail"),
    INVALID_NUMBER("invalidNumber"),
    FAILED("failed"),
    CANCELLED("cancelled");

    private final String value;

    CallOutcome(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CallOutcome fromValue(String raw) {
        return WireValues.parse(CallOutcome.class, raw);
    }

    public boolean isConnected() {
        return this == ANSWERED;
    }

    /** A wrong/dead number must not burn the retry budget - it ends the lead instead. */
    public boolean isPermanentFailure() {
        return this == INVALID_NUMBER;
    }

    public boolean isRetryable() {
        return this == NO_ANSWER || this == BUSY || this == REJECTED
                || this == VOICEMAIL || this == FAILED;
    }
}
