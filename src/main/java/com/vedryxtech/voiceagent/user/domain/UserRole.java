package com.vedryxtech.voiceagent.user.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Roles are stored bare and exposed to Spring Security as {@code ROLE_*} authorities,
 * so {@code hasRole('ORG_ADMIN')} works in the config.
 */
public enum UserRole implements WireValue {

    /** Cross-organization operator. Not scoped to a single tenant. */
    SUPER_ADMIN("superAdmin"),
    /** Owns one organization: manages users and settings. */
    ORG_ADMIN("orgAdmin"),
    /** Runs campaigns and sees every lead in the organization. */
    MANAGER("manager"),
    /** Works the assigned leads and logs call outcomes. */
    AGENT("agent"),
    /** Dashboard and recordings, read only. */
    VIEWER("viewer");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static UserRole fromValue(String raw) {
        return WireValues.parse(UserRole.class, raw);
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
