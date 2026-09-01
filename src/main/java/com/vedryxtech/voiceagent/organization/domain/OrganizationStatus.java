package com.vedryxtech.voiceagent.organization.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum OrganizationStatus implements WireValue {

    TRIAL("trial"),
    ACTIVE("active"),
    SUSPENDED("suspended");

    private final String value;

    OrganizationStatus(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OrganizationStatus fromValue(String raw) {
        return WireValues.parse(OrganizationStatus.class, raw);
    }

    public boolean canLogIn() {
        return this != SUSPENDED;
    }
}
