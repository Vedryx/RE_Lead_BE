package com.vedryxtech.voiceagent.dashboard.domain;

import com.vedryxtech.voiceagent.common.domain.WireValue;
import com.vedryxtech.voiceagent.common.domain.WireValues;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The time window the dashboard reports on.
 *
 * <p>The window is applied consistently to both sides of the dashboard: leads are filtered on
 * when they were <em>created</em>, calls on when they were <em>dialled</em>.</p>
 */
public enum DashboardRange implements WireValue {

    /** Since midnight, in the organization's own timezone. */
    TODAY("today", 1),
    /** The last 7 days. */
    WEEK("week", 7),
    /** The last 15 days. */
    FIFTEEN_DAYS("fifteenDays", 15),
    /** The last 30 days. */
    MONTH("month", 30),
    /** The last 90 days. */
    QUARTER("quarter", 90),
    /** Everything ever recorded. */
    ALL("all", 0),
    /** Whatever {@code from} and {@code to} say. */
    CUSTOM("custom", 0);

    private final String value;
    private final int days;

    DashboardRange(String value, int days) {
        this.value = value;
        this.days = days;
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static DashboardRange fromValue(String raw) {
        return WireValues.parse(DashboardRange.class, raw);
    }

    /** How many days the window spans. Zero for {@link #ALL} and {@link #CUSTOM}. */
    public int days() {
        return days;
    }

    public boolean isBounded() {
        return this != ALL;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }
}
