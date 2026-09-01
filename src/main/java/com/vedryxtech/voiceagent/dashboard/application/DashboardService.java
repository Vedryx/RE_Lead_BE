package com.vedryxtech.voiceagent.dashboard.application;

import com.vedryxtech.voiceagent.dashboard.domain.DashboardRange;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse;

import java.time.OffsetDateTime;

/** Read-only analytics over {@code lead} and {@code leads_log}. */
public interface DashboardService {

    /**
     * The whole landing page in one query set, measured over one window.
     *
     * <p>Where each number comes from is deliberate and consistent:</p>
     * <ul>
     *   <li><b>Current status</b> - the tiles, the pipeline and final-status breakdowns and the
     *       action-type split - is read from the {@code lead} documents, because only the lead
     *       knows where things stand right now.</li>
     *   <li><b>Everything historical</b> - call counts, connect rate, talk time, recordings,
     *       outcomes, dispositions and the daily trend - is read from {@code leads_log}, because
     *       only the log knows what actually happened, including the attempts that were
     *       superseded.</li>
     * </ul>
     *
     * @param range how far back to look
     * @param from  start of a {@code custom} window; ignored otherwise
     * @param to    end of a {@code custom} window; ignored otherwise
     */
    DashboardSummaryResponse summary(DashboardRange range, OffsetDateTime from, OffsetDateTime to);
}
