package com.vedryxtech.voiceagent.settings.domain;

import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global retry rules. Kept as data rather than code so the dialler can be tuned without
 * a redeploy and its decisions stay auditable.
 *
 * <p>Backoff is keyed by the {@link CallOutcome} wire value and the calling window is stored
 * as {@code HH:mm} strings, so the stored BSON reads the same way the API does.</p>
 *
 * <p>Relocated from {@code organization.call_policy} to {@code app_settings.call_policy} as
 * part of the single-tenant rework. Every {@code @Field} name is preserved, so existing
 * embedded BSON documents deserialise unchanged.</p>
 */
public class CallPolicy {

    /** How many times a lead may be dialled before it is marked exhausted. */
    @Field("max_attempts")
    private Integer maxAttempts = 4;

    /** Upper bound on attempts within one local day, so we never look like a robocaller. */
    @Field("max_attempts_per_day")
    private Integer maxAttemptsPerDay = 2;

    /** Minutes to wait before retrying, keyed by telephony outcome. */
    @Field("retry_backoff_minutes")
    private Map<String, Integer> retryBackoffMinutes = defaultBackoff();

    /** Earliest local time a call may be placed, {@code HH:mm}. */
    @Field("calling_window_start")
    private String callingWindowStart = "09:00";

    /** Latest local time a call may be placed, {@code HH:mm}. */
    @Field("calling_window_end")
    private String callingWindowEnd = "20:00";

    /** Record calls by default. Individual campaigns can still opt out. */
    @Field("recording_enabled")
    private Boolean recordingEnabled = Boolean.TRUE;

    // The three below govern what the agent may agree to on a call, not when it dials.
    // They were hardcoded in the voice agent, so a manager changing policy here changed
    // nothing about what the lead was promised.

    /** Earliest local time a site visit may be booked for, {@code HH:mm}. */
    @Field("visiting_hours_start")
    private String visitingHoursStart = "10:00";

    /** Latest local time a site visit may be booked for, {@code HH:mm}. */
    @Field("visiting_hours_end")
    private String visitingHoursEnd = "19:00";

    /** How far ahead a visit or callback may be booked. */
    @Field("booking_horizon_days")
    private Integer bookingHorizonDays = 30;

    /** How much notice the site needs before a visit. */
    @Field("visit_notice_minutes")
    private Integer visitNoticeMinutes = 30;

    public static CallPolicy defaults() {
        return new CallPolicy();
    }

    private static Map<String, Integer> defaultBackoff() {
        Map<String, Integer> backoff = new LinkedHashMap<>();
        backoff.put(CallOutcome.NO_ANSWER.getValue(), 120);
        backoff.put(CallOutcome.BUSY.getValue(), 30);
        backoff.put(CallOutcome.REJECTED.getValue(), 1440);
        backoff.put(CallOutcome.VOICEMAIL.getValue(), 240);
        backoff.put(CallOutcome.FAILED.getValue(), 15);
        return backoff;
    }

    /** Falls back to a sane delay when an outcome has no explicit rule. */
    public int backoffMinutesFor(CallOutcome outcome) {
        Map<String, Integer> backoff = retryBackoffMinutes == null ? defaultBackoff() : retryBackoffMinutes;
        Integer minutes = backoff.get(outcome.getValue());
        return minutes != null ? minutes : 60;
    }

    public LocalTime windowStart() {
        return parseOrDefault(callingWindowStart, LocalTime.of(9, 0));
    }

    public LocalTime windowEnd() {
        return parseOrDefault(callingWindowEnd, LocalTime.of(20, 0));
    }

    private static LocalTime parseOrDefault(String raw, LocalTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return LocalTime.parse(raw.trim());
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    public int maxAttemptsOrDefault() {
        return maxAttempts == null ? 4 : maxAttempts;
    }

    public int maxAttemptsPerDayOrDefault() {
        return maxAttemptsPerDay == null ? 2 : maxAttemptsPerDay;
    }

    public boolean recordingEnabledOrDefault() {
        return recordingEnabled == null || recordingEnabled;
    }

    public LocalTime visitingHoursStartOrDefault() {
        return parseOrDefault(visitingHoursStart, LocalTime.of(10, 0));
    }

    public LocalTime visitingHoursEndOrDefault() {
        return parseOrDefault(visitingHoursEnd, LocalTime.of(19, 0));
    }

    public int bookingHorizonDaysOrDefault() {
        return bookingHorizonDays == null || bookingHorizonDays < 1 ? 30 : bookingHorizonDays;
    }

    public int visitNoticeMinutesOrDefault() {
        return visitNoticeMinutes == null || visitNoticeMinutes < 0 ? 30 : visitNoticeMinutes;
    }

    public String getVisitingHoursStart() {
        return visitingHoursStart;
    }

    public void setVisitingHoursStart(String visitingHoursStart) {
        this.visitingHoursStart = visitingHoursStart;
    }

    public String getVisitingHoursEnd() {
        return visitingHoursEnd;
    }

    public void setVisitingHoursEnd(String visitingHoursEnd) {
        this.visitingHoursEnd = visitingHoursEnd;
    }

    public Integer getBookingHorizonDays() {
        return bookingHorizonDays;
    }

    public void setBookingHorizonDays(Integer bookingHorizonDays) {
        this.bookingHorizonDays = bookingHorizonDays;
    }

    public Integer getVisitNoticeMinutes() {
        return visitNoticeMinutes;
    }

    public void setVisitNoticeMinutes(Integer visitNoticeMinutes) {
        this.visitNoticeMinutes = visitNoticeMinutes;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Integer getMaxAttemptsPerDay() {
        return maxAttemptsPerDay;
    }

    public void setMaxAttemptsPerDay(Integer maxAttemptsPerDay) {
        this.maxAttemptsPerDay = maxAttemptsPerDay;
    }

    public Map<String, Integer> getRetryBackoffMinutes() {
        return retryBackoffMinutes;
    }

    public void setRetryBackoffMinutes(Map<String, Integer> retryBackoffMinutes) {
        this.retryBackoffMinutes = retryBackoffMinutes;
    }

    public String getCallingWindowStart() {
        return callingWindowStart;
    }

    public void setCallingWindowStart(String callingWindowStart) {
        this.callingWindowStart = callingWindowStart;
    }

    public String getCallingWindowEnd() {
        return callingWindowEnd;
    }

    public void setCallingWindowEnd(String callingWindowEnd) {
        this.callingWindowEnd = callingWindowEnd;
    }

    public Boolean getRecordingEnabled() {
        return recordingEnabled;
    }

    public void setRecordingEnabled(Boolean recordingEnabled) {
        this.recordingEnabled = recordingEnabled;
    }
}
