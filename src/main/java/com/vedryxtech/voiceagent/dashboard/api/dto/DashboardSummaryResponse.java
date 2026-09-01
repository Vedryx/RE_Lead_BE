package com.vedryxtech.voiceagent.dashboard.api.dto;

import com.vedryxtech.voiceagent.dashboard.domain.DashboardRange;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Everything the dashboard landing page needs, in one round trip: the headline tiles, the
 * status-wise breakdowns it renders as bars/donuts, call quality numbers and a daily trend.
 */
public record DashboardSummaryResponse(
        OffsetDateTime generatedAt,
        Window window,
        Totals totals,
        CallStats calls,
        List<CountBucket> byPipelineStatus,
        List<CountBucket> byFinalStatus,
        List<CountBucket> byActionType,
        List<CountBucket> byDisposition,
        List<CountBucket> byOutcome,
        List<DayBucket> dailyTrend
) {

    /**
     * The window every number below was measured over. {@code from} is null for {@code all}.
     */
    public record Window(
            DashboardRange range,
            OffsetDateTime from,
            OffsetDateTime to,
            int days,
            String timezone
    ) {
    }

    /** The headline tiles: how many are done, how many are still owed a call. */
    public record Totals(
            long totalLeads,
            long pending,
            long inProgress,
            long completed,
            long exhausted,
            long suppressed,
            long failed,
            long converted,
            long dueNow,
            long doNotCall
    ) {
    }

    /** Call quality, computed from {@code leads_log}. */
    public record CallStats(
            long totalAttempts,
            long connectedAttempts,
            long unansweredAttempts,
            double connectRatePercent,
            long totalTalkSeconds,
            double averageTalkSeconds,
            long recordingsAvailable,
            long attemptsToday,
            long connectedToday
    ) {
    }

    /** One slice of a status-wise breakdown. {@code key} is the enum wire value. */
    public record CountBucket(
            String key,
            String label,
            long count,
            double percentage
    ) {
    }

    public record DayBucket(
            LocalDate date,
            long attempts,
            long connected,
            long talkSeconds
    ) {
    }
}
