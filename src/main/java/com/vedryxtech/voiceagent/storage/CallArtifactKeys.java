package com.vedryxtech.voiceagent.storage;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Where one call's artifacts live.
 *
 * <p>Both keys are a pure function of the project, the date and the call log id, so the
 * CRM knows them before the phone rings. The alternative — letting egress name the file
 * from a template and learning the name from a webhook — means the CRM can say nothing
 * about a recording until minutes after the call ended.
 *
 * <pre>
 * recordings/{project-slug}/{yyyy}/{MM}/{callLogId}/
 *     audio.ogg          written by LiveKit egress
 *     transcript.json    written by this service
 * </pre>
 *
 * <p>There is no such thing as a URL for that prefix — S3-compatible storage has a flat
 * key space and {@code /} is an ordinary character. A presigned URL signs exactly one
 * object. So the prefix is stored, and a link is minted per artifact on request.
 */
public final class CallArtifactKeys {

    public static final String AUDIO = "audio.ogg";
    public static final String TRANSCRIPT = "transcript.json";

    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]+");
    private static final String UNFILED = "unfiled";

    private CallArtifactKeys() {
    }

    /**
     * The prefix for one call, with a trailing slash.
     *
     * <p>Slugged because spaces are legal in a key and a nuisance in every URL that
     * follows; partitioned by month because that is what a lifecycle rule matches on.
     */
    public static String prefix(String root, String project, OffsetDateTime at, String callLogId) {
        return "%s/%s/%04d/%02d/%s/".formatted(
                trimSlashes(root), slug(project), at.getYear(), at.getMonthValue(), callLogId);
    }

    public static String audioKey(String prefix) {
        return prefix + AUDIO;
    }

    public static String transcriptKey(String prefix) {
        return prefix + TRANSCRIPT;
    }

    static String slug(String value) {
        if (value == null || value.isBlank()) {
            return UNFILED;
        }
        String slug = NOT_SLUG.matcher(value.toLowerCase(Locale.ROOT)).replaceAll("-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.isBlank() ? UNFILED : slug;
    }

    private static String trimSlashes(String value) {
        return value == null ? "" : value.replaceAll("^/+|/+$", "");
    }
}
