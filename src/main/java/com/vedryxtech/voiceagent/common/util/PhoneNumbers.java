package com.vedryxtech.voiceagent.common.util;

/**
 * Canonicalises phone numbers so that {@code "+91 98765 43210"} and {@code "+919876543210"}
 * collide on the unique {@code calling_phone} index instead of creating two leads.
 *
 * <p>Formatting characters are stripped; a leading {@code +} is preserved. A bare 10-digit
 * national number is promoted to E.164 using the configured default region (India, +91).
 * That closes the "same person becomes two leads" bug: importers routinely paste
 * {@code 9876543210} for a number the agent later reaches as {@code +919876543210}, and
 * without a common form the unique index does not catch the duplicate.</p>
 *
 * <p>The default region is India, matching where the CRM operates today. If the company
 * expands to other markets, this becomes a per-tenant setting on {@code app_settings};
 * for now, keeping it a compile-time constant makes the behaviour easy to audit.</p>
 */
public final class PhoneNumbers {

    /** ITU-T E.164 caps the number of digits at 15. Below 8 is not a real phone. */
    private static final int E164_MIN_DIGITS = 8;
    private static final int E164_MAX_DIGITS = 15;

    /** India — the CRM's home market. Applied only to bare 10-digit numbers. */
    private static final String DEFAULT_COUNTRY_CODE = "91";
    private static final int NATIONAL_NUMBER_LENGTH = 10;

    private PhoneNumbers() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String cleaned = trimmed.replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) {
            return null;
        }

        boolean international = cleaned.startsWith("+");
        String digits = cleaned.replaceAll("[^0-9]", "");
        // "00" is the ITU international-access prefix; strip it and treat as +.
        if (!international && digits.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
            international = true;
        }
        if (digits.isEmpty()) {
            return null;
        }

        // M-11: promote a bare 10-digit national number to E.164 with the default
        // country code, so "9876543210" and "+919876543210" collide on the unique
        // index. Reject a leading zero — those look like trunk-prefixed local numbers
        // and we do not want to guess.
        if (!international && digits.length() == NATIONAL_NUMBER_LENGTH && !digits.startsWith("0")) {
            digits = DEFAULT_COUNTRY_CODE + digits;
            international = true;
        }

        // Basic E.164 sanity. Below 8 digits is definitely not a number a dialler can
        // reach — return the digits unprefixed so downstream code can see it is broken.
        if (international && (digits.length() < E164_MIN_DIGITS || digits.length() > E164_MAX_DIGITS)) {
            return digits;
        }

        return international ? "+" + digits : digits;
    }
}
