package com.vedryxtech.voiceagent.user.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.vedryxtech.voiceagent.common.domain.WireValue;

import java.util.Locale;

/**
 * The dashboard has two roles now: ADMIN (settings, user management, api-key rotation) and
 * MEMBER (everything else humans do — leads, calls, dashboard).
 *
 * <p>The agent authenticates as {@code API_CLIENT} via the API-key filter, so that role does
 * not live on this enum; the filter assigns the authority directly.</p>
 *
 * <p>Legacy roles (from the pre-single-tenant docs) are remapped on read via
 * {@link #fromValue(String)}: {@code orgAdmin}/{@code superAdmin} become ADMIN; every other
 * legacy value becomes MEMBER. This keeps existing {@code app_user} documents readable.</p>
 */
public enum UserRole implements WireValue {

    /** Manages users, settings, api-key rotation. */
    ADMIN("admin"),
    /** Works leads, places calls, reads the dashboard. */
    MEMBER("member");

    private final String value;

    UserRole(String value) {
        this.value = value;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Accepts current wire values ({@code admin}, {@code member}) and legacy ones
     * ({@code orgAdmin}, {@code superAdmin}, {@code manager}, {@code agent}, {@code viewer}).
     * Case- and underscore-insensitive.
     */
    @JsonCreator
    public static UserRole fromValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return switch (key) {
            case "admin", "orgadmin", "superadmin" -> ADMIN;
            case "member", "manager", "agent", "viewer" -> MEMBER;
            default -> throw new IllegalArgumentException(
                    "Unknown UserRole '" + raw + "'. Allowed: admin, member "
                            + "(legacy: orgAdmin, superAdmin, manager, agent, viewer)");
        };
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
