package com.vedryxtech.voiceagent.call.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Timeline entries appended to a call attempt. Nothing is ever overwritten. */
public enum CallEventType implements WireValue {

    QUEUED("queued"),
    DIAL_STARTED("dialStarted"),
    RINGING("ringing"),
    ANSWERED("answered"),
    HANGUP("hangup"),
    OUTCOME_RECORDED("outcomeRecorded"),
    DISPOSITION_SET("dispositionSet"),
    RETRY_SCHEDULED("retryScheduled"),
    CALLBACK_REQUESTED("callbackRequested"),
    RETRIES_EXHAUSTED("retriesExhausted"),
    STATUS_CHANGED("statusChanged"),
    RECORDING_STARTED("recordingStarted"),
    RECORDING_READY("recordingReady"),
    TRANSCRIPT_READY("transcriptReady"),
    NOTE("note"),
    ERROR("error");

    private final String value;

    CallEventType(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CallEventType fromValue(String raw) {
        return WireValues.parse(CallEventType.class, raw);
    }
}
