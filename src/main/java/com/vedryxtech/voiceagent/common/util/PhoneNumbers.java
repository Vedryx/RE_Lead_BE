package com.vedryxtech.voiceagent.common.util;

/**
 * Canonicalises phone numbers so that {@code "+91 98765 43210"} and {@code "+919876543210"}
 * collide on the unique {@code calling_phone} index instead of creating two leads.
 *
 * <p>Formatting characters are stripped; a leading {@code +} is preserved. No country code is
 * inferred, so a bare national number stays distinct from its E.164 form.</p>
 */
public final class PhoneNumbers {

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
        boolean international = cleaned.startsWith("+") || cleaned.startsWith("00");
        String digits = cleaned.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("00") && digits.length() > 2) {
            digits = digits.substring(2);
        }
        if (digits.isEmpty()) {
            return null;
        }
        return international ? "+" + digits : digits;
    }
}
