package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.exception.InvalidStateTransitionException;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * The rules around the one field that records a person's word.
 *
 * <p>Separate from {@code LeadServiceImpl} so it can be exercised without a database:
 * these are the rules most worth a test and least worth an integration harness.
 */
final class LeadConsent {

    private LeadConsent() {
    }

    /**
     * Guard the clearing of {@code doNotCall}.
     *
     * <p>Someone asked not to be called. Undoing that must be deliberate: a reason is
     * required, and it is written into the notes so there is an answer to "who decided we
     * could ring them again". Without this, a form that PATCHes every field it holds
     * quietly re-enables dialling whenever a checkbox is unticked.
     *
     * <p>The restore matters as much as the guard. Clearing the flag alone left the lead
     * in {@code SUPPRESSED} with nothing to move it — callable on paper, never called in
     * practice, which is worse than either honest answer.
     */
    static void applyClearance(Lead lead, boolean wasSuppressed, String reason) {
        if (!wasSuppressed || lead.isDoNotCall()) {
            return;
        }
        if (reason == null || reason.isBlank()) {
            lead.setDoNotCall(Boolean.TRUE);
            throw new InvalidStateTransitionException(
                    "Lead " + lead.getIdAsString() + " asked not to be called. Send "
                            + "doNotCallClearedReason to make them callable again.");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String entry = "[" + now + "] do_not_call cleared: " + reason.trim();
        lead.setNotes(lead.getNotes() == null || lead.getNotes().isBlank()
                ? entry
                : lead.getNotes() + System.lineSeparator() + entry);

        if (lead.getPipelineStatus() == LeadPipelineStatus.SUPPRESSED) {
            lead.setPipelineStatus(LeadPipelineStatus.QUEUED);
            lead.setNextAttemptAt(now);
        }
    }

    /** A lead marked do-not-call is taken out of the queue, not merely flagged. */
    static void applySuppression(Lead lead) {
        if (lead.isDoNotCall()) {
            lead.setPipelineStatus(LeadPipelineStatus.SUPPRESSED);
            lead.setNextAttemptAt(null);
        }
    }
}
