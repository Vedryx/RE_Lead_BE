package com.vedryxtech.voiceagent.call.domain;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One immutable line in a call attempt's timeline. Embedded in {@link LeadCallLog} so the
 * whole story of an attempt is a single read, with no join and no second collection.
 */
public class CallEvent {

    private OffsetDateTime at;

    private CallEventType type;

    private String message;

    /** Free-form context: previous status, requested callback time, error body. */
    private Map<String, Object> data = new LinkedHashMap<>();

    public CallEvent() {
    }

    public CallEvent(CallEventType type, String message) {
        this.at = OffsetDateTime.now(ZoneOffset.UTC);
        this.type = type;
        this.message = message;
    }

    public CallEvent with(String key, Object value) {
        if (value != null) {
            data.put(key, value);
        }
        return this;
    }

    public static CallEvent of(CallEventType type, String message) {
        return new CallEvent(type, message);
    }

    public OffsetDateTime getAt() {
        return at;
    }

    public void setAt(OffsetDateTime at) {
        this.at = at;
    }

    public CallEventType getType() {
        return type;
    }

    public void setType(CallEventType type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
