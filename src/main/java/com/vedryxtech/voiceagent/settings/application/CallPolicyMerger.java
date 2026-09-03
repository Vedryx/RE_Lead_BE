package com.vedryxtech.voiceagent.settings.application;

import com.vedryxtech.voiceagent.exception.ValidationException;
import com.vedryxtech.voiceagent.settings.api.dto.CallPolicyPatchRequest;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a partial patch into a merge onto the stored policy, validating as it goes. Any
 * problem is collected into a single fieldErrors envelope and thrown as a
 * {@link ValidationException} (rendered 422).
 *
 * <p>Bounds picked for real-world sanity, not to match the DTO's Bean Validation (there is
 * none, deliberately — see the DTO's javadoc).</p>
 */
public final class CallPolicyMerger {

    /** Retries above this look like a robocall; the QA harness rejected 100000. */
    private static final int MAX_ATTEMPTS_UPPER_BOUND = 20;
    private static final int BACKOFF_MINUTE_UPPER_BOUND = 10_080; // one week

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final Pattern HH_MM_STRICT = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /** Known keys, so a typoed outcome name in a backoff map does not silently do nothing. */
    private static final Set<String> KNOWN_OUTCOMES = Set.of(
            "noAnswer", "busy", "rejected", "voicemail", "failed", "answered", "invalidNumber");

    private CallPolicyMerger() {
    }

    /**
     * Copy the stored policy, overlay non-null patch fields, validate, and return the merged
     * result. The stored policy is not mutated — the caller decides whether to persist.
     */
    public static CallPolicy merge(CallPolicy stored, CallPolicyPatchRequest patch) {
        CallPolicy merged = copy(stored);
        Map<String, String> errors = new LinkedHashMap<>();

        if (patch.maxAttempts() != null) {
            requireBounded(errors, "maxAttempts", patch.maxAttempts(), 1, MAX_ATTEMPTS_UPPER_BOUND);
            merged.setMaxAttempts(patch.maxAttempts());
        }
        if (patch.maxAttemptsPerDay() != null) {
            requireBounded(errors, "maxAttemptsPerDay", patch.maxAttemptsPerDay(), 1, MAX_ATTEMPTS_UPPER_BOUND);
            merged.setMaxAttemptsPerDay(patch.maxAttemptsPerDay());
        }
        if (patch.retryBackoffMinutes() != null) {
            Map<String, Integer> backoff = mergeBackoff(stored, patch.retryBackoffMinutes(), errors);
            merged.setRetryBackoffMinutes(backoff);
        }
        if (patch.callingWindowStart() != null) {
            requireHhMm(errors, "callingWindowStart", patch.callingWindowStart());
            merged.setCallingWindowStart(patch.callingWindowStart());
        }
        if (patch.callingWindowEnd() != null) {
            requireHhMm(errors, "callingWindowEnd", patch.callingWindowEnd());
            merged.setCallingWindowEnd(patch.callingWindowEnd());
        }
        if (patch.recordingEnabled() != null) {
            merged.setRecordingEnabled(patch.recordingEnabled());
        }
        if (patch.visitingHoursStart() != null) {
            requireHhMm(errors, "visitingHoursStart", patch.visitingHoursStart());
            merged.setVisitingHoursStart(patch.visitingHoursStart());
        }
        if (patch.visitingHoursEnd() != null) {
            requireHhMm(errors, "visitingHoursEnd", patch.visitingHoursEnd());
            merged.setVisitingHoursEnd(patch.visitingHoursEnd());
        }
        if (patch.bookingHorizonDays() != null) {
            requireBounded(errors, "bookingHorizonDays", patch.bookingHorizonDays(), 1, 365);
            merged.setBookingHorizonDays(patch.bookingHorizonDays());
        }
        if (patch.visitNoticeMinutes() != null) {
            requireBounded(errors, "visitNoticeMinutes", patch.visitNoticeMinutes(), 0, 1440);
            merged.setVisitNoticeMinutes(patch.visitNoticeMinutes());
        }

        // Cross-field: only meaningful if both sides parse. Skip when either side already failed
        // above so we do not stack a confusing second error on top of a parse error.
        requireOrderedWindow(errors, "callingWindowStart", "callingWindowEnd",
                merged.getCallingWindowStart(), merged.getCallingWindowEnd());
        requireOrderedWindow(errors, "visitingHoursStart", "visitingHoursEnd",
                merged.getVisitingHoursStart(), merged.getVisitingHoursEnd());

        if (!errors.isEmpty()) {
            throw new ValidationException("Call policy validation failed", errors);
        }
        return merged;
    }

    private static Map<String, Integer> mergeBackoff(CallPolicy stored, Map<String, Integer> patch,
                                                     Map<String, String> errors) {
        Map<String, Integer> base = stored.getRetryBackoffMinutes() != null
                ? new LinkedHashMap<>(stored.getRetryBackoffMinutes())
                : new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : patch.entrySet()) {
            String outcome = entry.getKey();
            Integer minutes = entry.getValue();
            String path = "retryBackoffMinutes." + outcome;
            if (outcome == null || outcome.isBlank()) {
                errors.put("retryBackoffMinutes", "outcome key must not be blank");
                continue;
            }
            if (!KNOWN_OUTCOMES.contains(outcome)) {
                errors.put(path, "unknown outcome '" + outcome + "'");
                continue;
            }
            if (minutes == null) {
                errors.put(path, "minutes must not be null");
                continue;
            }
            if (minutes < 0 || minutes > BACKOFF_MINUTE_UPPER_BOUND) {
                errors.put(path, "must be between 0 and " + BACKOFF_MINUTE_UPPER_BOUND);
                continue;
            }
            base.put(outcome, minutes);
        }
        return base;
    }

    private static void requireBounded(Map<String, String> errors, String field, int value, int min, int max) {
        if (value < min || value > max) {
            errors.put(field, "must be between " + min + " and " + max);
        }
    }

    private static void requireHhMm(Map<String, String> errors, String field, String raw) {
        if (raw == null || raw.isBlank()) {
            errors.put(field, "must not be blank");
            return;
        }
        if (!HH_MM_STRICT.matcher(raw).matches()) {
            errors.put(field, "must be HH:mm (00:00-23:59)");
            return;
        }
        try {
            LocalTime.parse(raw, HH_MM);
        } catch (DateTimeParseException ex) {
            errors.put(field, "must be a valid HH:mm time");
        }
    }

    private static void requireOrderedWindow(Map<String, String> errors, String startField,
                                             String endField, String startRaw, String endRaw) {
        // Do not layer on a parse error above.
        Set<String> alreadyBroken = new LinkedHashSet<>(errors.keySet());
        if (alreadyBroken.contains(startField) || alreadyBroken.contains(endField)) {
            return;
        }
        try {
            LocalTime start = LocalTime.parse(startRaw, HH_MM);
            LocalTime end = LocalTime.parse(endRaw, HH_MM);
            if (!start.isBefore(end)) {
                errors.put(endField, endField + " must be after " + startField);
            }
        } catch (DateTimeParseException ex) {
            // Only reached when a stored value is malformed; ignore.
        }
    }

    private static CallPolicy copy(CallPolicy src) {
        CallPolicy dest = CallPolicy.defaults();
        if (src == null) {
            return dest;
        }
        dest.setMaxAttempts(src.getMaxAttempts());
        dest.setMaxAttemptsPerDay(src.getMaxAttemptsPerDay());
        dest.setRetryBackoffMinutes(src.getRetryBackoffMinutes() == null
                ? null : new LinkedHashMap<>(src.getRetryBackoffMinutes()));
        dest.setCallingWindowStart(src.getCallingWindowStart());
        dest.setCallingWindowEnd(src.getCallingWindowEnd());
        dest.setRecordingEnabled(src.getRecordingEnabled());
        dest.setVisitingHoursStart(src.getVisitingHoursStart());
        dest.setVisitingHoursEnd(src.getVisitingHoursEnd());
        dest.setBookingHorizonDays(src.getBookingHorizonDays());
        dest.setVisitNoticeMinutes(src.getVisitNoticeMinutes());
        return dest;
    }
}
