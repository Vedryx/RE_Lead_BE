package com.vedryxtech.voiceagent.settings.api.dto;

import java.util.Map;

/**
 * Partial update for the calling rules. Every field is nullable: a missing key means "leave
 * the stored value alone", rather than the pre-rework behaviour where Jackson bound a fresh
 * {@code CallPolicy} and silently reset omitted fields to class defaults.
 *
 * <p>Validation lives in the service layer rather than on bean annotations because several
 * of the checks are cross-field (start &lt; end, hh:mm parseability, per-outcome backoff
 * bounds) and need to build a single 422 fieldErrors envelope.</p>
 */
public record CallPolicyPatchRequest(
        Integer maxAttempts,
        Integer maxAttemptsPerDay,
        Map<String, Integer> retryBackoffMinutes,
        String callingWindowStart,
        String callingWindowEnd,
        Boolean recordingEnabled,
        String visitingHoursStart,
        String visitingHoursEnd,
        Integer bookingHorizonDays,
        Integer visitNoticeMinutes
) {
}
