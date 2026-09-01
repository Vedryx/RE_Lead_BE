package com.vedryxtech.voiceagent.call.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle of the recording attached to a call attempt. */
public enum RecordingStatus implements WireValue {

    NOT_REQUESTED("notRequested"),
    STARTING("starting"),
    RECORDING("recording"),
    PROCESSING("processing"),
    AVAILABLE("available"),
    FAILED("failed"),
    EXPIRED("expired");

    private final String value;

    RecordingStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static RecordingStatus fromValue(String raw) {
        return WireValues.parse(RecordingStatus.class, raw);
    }

    public boolean isPlayable() {
        return this == AVAILABLE;
    }
}
