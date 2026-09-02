package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.exception.InvalidStateTransitionException;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A person's request not to be called is the one field a stray form submission must
 * not undo.
 */
class LeadConsentTest {

    @Test
    void clearing_it_without_a_reason_is_refused_and_the_flag_survives() {
        Lead lead = suppressed();
        lead.setDoNotCall(Boolean.FALSE);   // what the PATCH asked for

        assertThatThrownBy(() -> LeadConsent.applyClearance(lead, true, null))
                .isInstanceOf(InvalidStateTransitionException.class)
                .hasMessageContaining("doNotCallClearedReason");
        assertThat(lead.isDoNotCall())
                .as("a refused clearance must leave them suppressed, not half-cleared")
                .isTrue();
    }

    @Test
    void a_blank_reason_is_no_reason() {
        Lead lead = suppressed();
        lead.setDoNotCall(Boolean.FALSE);

        assertThatThrownBy(() -> LeadConsent.applyClearance(lead, true, "   "))
                .isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void a_stated_reason_clears_it_and_leaves_a_record() {
        Lead lead = suppressed();
        lead.setNotes("enquired via website");
        lead.setDoNotCall(Boolean.FALSE);

        LeadConsent.applyClearance(lead, true, "wrong number corrected by Priya");

        assertThat(lead.isDoNotCall()).isFalse();
        assertThat(lead.getNotes())
                .contains("enquired via website")
                .contains("do_not_call cleared: wrong number corrected by Priya");
    }

    @Test
    void clearing_it_also_makes_the_lead_reachable_again() {
        // Clearing the flag alone left the lead SUPPRESSED for ever: callable on paper,
        // never claimed in practice.
        Lead lead = suppressed();
        lead.setDoNotCall(Boolean.FALSE);

        LeadConsent.applyClearance(lead, true, "consent re-obtained");

        assertThat(lead.getPipelineStatus()).isEqualTo(LeadPipelineStatus.QUEUED);
        assertThat(lead.getNextAttemptAt()).isNotNull();
    }

    @Test
    void a_patch_that_touches_nothing_else_is_left_alone() {
        Lead lead = new Lead();
        lead.setPipelineStatus(LeadPipelineStatus.QUEUED);
        lead.setDoNotCall(Boolean.FALSE);

        LeadConsent.applyClearance(lead, false, null);

        assertThat(lead.getPipelineStatus()).isEqualTo(LeadPipelineStatus.QUEUED);
        assertThat(lead.getNotes()).isNull();
    }

    @Test
    void setting_it_needs_no_reason_and_takes_them_out_of_the_queue() {
        Lead lead = new Lead();
        lead.setPipelineStatus(LeadPipelineStatus.QUEUED);
        lead.setDoNotCall(Boolean.TRUE);

        LeadConsent.applyClearance(lead, false, null);
        LeadConsent.applySuppression(lead);

        assertThat(lead.getPipelineStatus()).isEqualTo(LeadPipelineStatus.SUPPRESSED);
        assertThat(lead.getNextAttemptAt()).isNull();
    }

    private static Lead suppressed() {
        Lead lead = new Lead();
        lead.setDoNotCall(Boolean.TRUE);
        lead.setPipelineStatus(LeadPipelineStatus.SUPPRESSED);
        lead.setNextAttemptAt(null);
        return lead;
    }
}
