package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.api.dto.LeadPatchRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * CRUD over the {@code lead} collection.
 *
 * <p>A lead is created fresh - a name and a number - and is queued for a call immediately.
 * The action fields are filled in later by
 * {@link CallOrchestrationService#recordOutcome(String, com.vedryxtech.voiceagent.call.api.dto.CallOutcomeRequest)}.</p>
 */
public interface LeadService {

    Lead create(LeadRequest request);

    /** Creates the lead, or updates the one that already owns this phone number. */
    UpsertResult upsert(LeadRequest request);

    Lead getById(String id);

    Page<Lead> search(LeadSearchCriteria criteria, Pageable pageable);

    /** Full replace: fields omitted from the payload are cleared. */
    Lead replace(String id, LeadRequest request);

    /** Partial update: null fields are left as they are. */
    Lead patch(String id, LeadPatchRequest request);

    /** Carries whether {@link #upsert(LeadRequest)} inserted (201) or updated (200). */
    record UpsertResult(Lead lead, boolean created) {
    }
}
