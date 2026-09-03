package com.vedryxtech.voiceagent.settings.application;

import com.vedryxtech.voiceagent.exception.ValidationException;
import com.vedryxtech.voiceagent.settings.api.dto.CallPolicyPatchRequest;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pre-rework PUT bound a fresh {@code CallPolicy}, so any field left out silently reset to
 * class defaults — H-1 in the QA report. These tests prove that partial patches now preserve
 * omitted fields and that the bounds rejects hit before the write.
 */
class CallPolicyMergerTest {

    @Test
    void a_partial_patch_leaves_omitted_fields_alone() {
        CallPolicy stored = CallPolicy.defaults();
        stored.setMaxAttempts(6);
        stored.setCallingWindowStart("07:30");
        stored.setCallingWindowEnd("21:00");
        stored.setVisitNoticeMinutes(45);

        CallPolicyPatchRequest patch = new CallPolicyPatchRequest(
                null, null,
                Map.of("busy", 45),   // only the busy backoff is being changed
                null, null, null, null, null, null, null);

        CallPolicy merged = CallPolicyMerger.merge(stored, patch);

        // Everything the caller did not touch survives.
        assertThat(merged.getMaxAttempts()).isEqualTo(6);
        assertThat(merged.getCallingWindowStart()).isEqualTo("07:30");
        assertThat(merged.getCallingWindowEnd()).isEqualTo("21:00");
        assertThat(merged.getVisitNoticeMinutes()).isEqualTo(45);
        // The touched field, and the other backoff entries, are both correct.
        assertThat(merged.getRetryBackoffMinutes()).containsEntry("busy", 45)
                .containsEntry("noAnswer", 120);
    }

    @Test
    void the_stored_policy_is_never_mutated() {
        CallPolicy stored = CallPolicy.defaults();
        stored.setMaxAttempts(3);

        CallPolicyMerger.merge(stored, new CallPolicyPatchRequest(
                7, null, null, null, null, null, null, null, null, null));

        assertThat(stored.getMaxAttempts()).isEqualTo(3);
    }

    @Test
    void negative_and_out_of_bounds_values_are_rejected_together() {
        CallPolicy stored = CallPolicy.defaults();
        CallPolicyPatchRequest patch = new CallPolicyPatchRequest(
                -1, 100_000, null,
                "25:99", null, null, null, null, 0, -3);

        assertThatThrownBy(() -> CallPolicyMerger.merge(stored, patch))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> {
                    ValidationException v = (ValidationException) ex;
                    assertThat(v.getFieldErrors())
                            .containsKeys("maxAttempts", "maxAttemptsPerDay",
                                    "callingWindowStart", "bookingHorizonDays", "visitNoticeMinutes");
                });
    }

    @Test
    void the_calling_window_must_open_before_it_closes() {
        CallPolicy stored = CallPolicy.defaults();
        CallPolicyPatchRequest patch = new CallPolicyPatchRequest(
                null, null, null,
                "20:00", "09:00",
                null, null, null, null, null);

        assertThatThrownBy(() -> CallPolicyMerger.merge(stored, patch))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors())
                        .containsKey("callingWindowEnd"));
    }

    @Test
    void an_absurd_backoff_and_an_unknown_outcome_both_surface() {
        CallPolicy stored = CallPolicy.defaults();
        Map<String, Integer> backoff = new LinkedHashMap<>();
        backoff.put("noAnswer", -99_999);
        backoff.put("madeUpOutcome", 30);
        CallPolicyPatchRequest patch = new CallPolicyPatchRequest(
                null, null, backoff, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> CallPolicyMerger.merge(stored, patch))
                .isInstanceOf(ValidationException.class)
                .satisfies(ex -> assertThat(((ValidationException) ex).getFieldErrors())
                        .containsKeys("retryBackoffMinutes.noAnswer",
                                "retryBackoffMinutes.madeUpOutcome"));
    }

    @Test
    void hh_mm_must_be_two_digits_of_each() {
        CallPolicy stored = CallPolicy.defaults();
        // "9:00" (missing leading zero) used to silently store and later parse to 09:00.
        // That masked typos — reject strictly.
        CallPolicyPatchRequest patch = new CallPolicyPatchRequest(
                null, null, null, "9:00", null, null, null, null, null, null);

        assertThatThrownBy(() -> CallPolicyMerger.merge(stored, patch))
                .isInstanceOf(ValidationException.class);
    }
}
