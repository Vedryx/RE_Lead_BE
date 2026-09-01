package com.vedryxtech.voiceagent.common.domain;

import java.util.Arrays;
import java.util.Locale;

/**
 * Shared lookup for {@link WireValue} enums.
 *
 * <p>Values go out as camelCase ({@code retryScheduled}), but parsing is deliberately forgiving:
 * underscores and case are ignored, so {@code retryScheduled}, {@code retry_scheduled} and
 * {@code RETRY_SCHEDULED} all resolve to the same constant. That means a caller written against
 * the older snake_case wire format keeps working.</p>
 */
public final class WireValues {

    private WireValues() {
    }

    public static <E extends Enum<E> & WireValue> E parse(Class<E> type, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = canonical(raw);
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> canonical(constant.getValue()).equals(normalized)
                        || canonical(constant.name()).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown " + type.getSimpleName() + " '" + raw + "'. Allowed: "
                                + Arrays.stream(type.getEnumConstants())
                                .map(WireValue::getValue)
                                .toList()));
    }

    /** Strips case and underscores so every spelling of the same value compares equal. */
    private static String canonical(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replace("_", "");
    }
}
