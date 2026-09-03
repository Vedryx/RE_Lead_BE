package com.vedryxtech.voiceagent;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The rules that decide whether a lead is dialled again, and when. */
class CallPolicyTest {

    @Test
    void backsOffPerOutcome() {
        CallPolicy policy = CallPolicy.defaults();

        assertThat(policy.backoffMinutesFor(CallOutcome.BUSY)).isEqualTo(30);
        assertThat(policy.backoffMinutesFor(CallOutcome.NO_ANSWER)).isEqualTo(120);
        // A rejection is a soft "no": wait a full day rather than calling back in an hour.
        assertThat(policy.backoffMinutesFor(CallOutcome.REJECTED)).isEqualTo(1440);
    }

    @Test
    void fallsBackToAnHourForAnUnconfiguredOutcome() {
        CallPolicy policy = CallPolicy.defaults();
        policy.setRetryBackoffMinutes(Map.of(CallOutcome.BUSY.getValue(), 5));

        assertThat(policy.backoffMinutesFor(CallOutcome.BUSY)).isEqualTo(5);
        assertThat(policy.backoffMinutesFor(CallOutcome.NO_ANSWER)).isEqualTo(60);
    }

    @Test
    void survivesAnUnparseableCallingWindow() {
        CallPolicy policy = CallPolicy.defaults();
        policy.setCallingWindowStart("not-a-time");

        assertThat(policy.windowStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(policy.windowEnd()).isEqualTo(LocalTime.of(20, 0));
    }

    @Test
    void unansweredOutcomesRetryButDeadNumbersDoNot() {
        assertThat(CallOutcome.NO_ANSWER.isRetryable()).isTrue();
        assertThat(CallOutcome.BUSY.isRetryable()).isTrue();
        assertThat(CallOutcome.REJECTED.isRetryable()).isTrue();

        assertThat(CallOutcome.INVALID_NUMBER.isRetryable()).isFalse();
        assertThat(CallOutcome.INVALID_NUMBER.isPermanentFailure()).isTrue();
        assertThat(CallOutcome.ANSWERED.isConnected()).isTrue();
    }

    @Test
    void pendingCoversEverythingStillOwedACall() {
        assertThat(LeadPipelineStatus.NEW.isPending()).isTrue();
        assertThat(LeadPipelineStatus.QUEUED.isPending()).isTrue();
        assertThat(LeadPipelineStatus.RETRY_SCHEDULED.isPending()).isTrue();
        assertThat(LeadPipelineStatus.CALLBACK_SCHEDULED.isPending()).isTrue();

        assertThat(LeadPipelineStatus.DIALING.isPending()).isFalse();
        assertThat(LeadPipelineStatus.DIALING.isActive()).isTrue();
        assertThat(LeadPipelineStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(LeadPipelineStatus.SUPPRESSED.isTerminal()).isTrue();
    }

    @Test
    void dispositionsSplitIntoFollowUpAndClosed() {
        assertThat(CallDisposition.CALLBACK_REQUESTED.requiresFollowUp()).isTrue();
        assertThat(CallDisposition.RESCHEDULED.requiresFollowUp()).isTrue();

        assertThat(CallDisposition.SITE_VISIT_BOOKED.isFinal()).isTrue();
        assertThat(CallDisposition.NOT_INTERESTED.isFinal()).isTrue();
        assertThat(CallDisposition.DO_NOT_CALL.isFinal()).isTrue();
        assertThat(CallDisposition.NO_DECISION.isFinal()).isFalse();
    }

    @Test
    void enumsEmitCamelCaseAndAcceptEitherSpelling() {
        assertThat(CallOutcome.NO_ANSWER.getValue()).isEqualTo("noAnswer");
        assertThat(CallOutcome.fromValue("noAnswer")).isEqualTo(CallOutcome.NO_ANSWER);
        // Older callers still speak snake_case; both spellings resolve to the same constant.
        assertThat(CallOutcome.fromValue("no_answer")).isEqualTo(CallOutcome.NO_ANSWER);
        assertThat(CallOutcome.fromValue("NO_ANSWER")).isEqualTo(CallOutcome.NO_ANSWER);
        assertThat(LeadPipelineStatus.fromValue("retryScheduled"))
                .isEqualTo(LeadPipelineStatus.RETRY_SCHEDULED);
        assertThat(LeadPipelineStatus.fromValue("retry_scheduled"))
                .isEqualTo(LeadPipelineStatus.RETRY_SCHEDULED);
    }
}
