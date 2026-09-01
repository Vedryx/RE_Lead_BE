package com.vedryxtech.voiceagent.call.application;

import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;

/** Read side of {@code leads_log}: the follow-up history and the recordings list. */
public interface LeadCallLogService {

    LeadCallLog require(String callLogId);

    /** Every attempt for one lead, newest first. This is the follow-up timeline. */
    List<LeadCallLog> historyForLead(String leadId);

    Page<LeadCallLog> search(CallLogSearchCriteria criteria, Pageable pageable);

    /** Attempts that produced a playable recording, newest first. */
    Page<LeadCallLog> recordings(Pageable pageable);

    record CallLogSearchCriteria(
            String leadId,
            String phone,
            String outcome,
            String disposition,
            String recordingStatus,
            Boolean hasRecording,
            OffsetDateTime from,
            OffsetDateTime to
    ) {
    }
}
