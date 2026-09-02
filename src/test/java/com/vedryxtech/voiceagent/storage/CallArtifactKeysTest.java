package com.vedryxtech.voiceagent.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both artifacts of one call share a prefix, and that prefix is knowable before the phone
 * rings — which is the whole point of not letting egress name the file.
 */
class CallArtifactKeysTest {

    private static final OffsetDateTime SEPTEMBER =
            OffsetDateTime.of(2026, 9, 2, 15, 4, 0, 0, ZoneOffset.UTC);

    @Test
    void audio_and_transcript_live_together() {
        String prefix = CallArtifactKeys.prefix("recordings", "My Home Sanctuary", SEPTEMBER, "abc123");

        assertThat(prefix).isEqualTo("recordings/my-home-sanctuary/2026/09/abc123/");
        assertThat(CallArtifactKeys.audioKey(prefix))
                .isEqualTo("recordings/my-home-sanctuary/2026/09/abc123/audio.ogg");
        assertThat(CallArtifactKeys.transcriptKey(prefix))
                .isEqualTo("recordings/my-home-sanctuary/2026/09/abc123/transcript.json");
    }

    @Test
    void the_month_is_zero_padded_so_a_lifecycle_rule_can_match_it() {
        OffsetDateTime january = OffsetDateTime.of(2027, 1, 9, 0, 0, 0, 0, ZoneOffset.UTC);

        assertThat(CallArtifactKeys.prefix("recordings", "P", january, "x"))
                .contains("/2027/01/");
    }

    @ParameterizedTest
    @CsvSource({
            "My Home Sanctuary,      my-home-sanctuary",
            "Riverside  Greens,      riverside-greens",
            "Goyal's Project #2,     goyal-s-project-2",
            "  spaced  ,             spaced",
            "प्रोजेक्ट,                unfiled",
    })
    void a_project_name_becomes_something_a_url_can_carry(String project, String expected) {
        assertThat(CallArtifactKeys.slug(project)).isEqualTo(expected);
    }

    @Test
    void a_lead_with_no_project_still_gets_a_home() {
        // Better one bucket of unfiled calls than a key beginning "recordings//2026".
        assertThat(CallArtifactKeys.slug(null)).isEqualTo("unfiled");
        assertThat(CallArtifactKeys.slug("   ")).isEqualTo("unfiled");
    }

    @Test
    void the_root_is_normalised_so_a_stray_slash_cannot_double_up() {
        assertThat(CallArtifactKeys.prefix("/recordings/", "P", SEPTEMBER, "x"))
                .startsWith("recordings/")
                .doesNotContain("//");
    }
}
