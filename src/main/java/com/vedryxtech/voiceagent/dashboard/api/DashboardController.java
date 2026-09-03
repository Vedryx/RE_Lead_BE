package com.vedryxtech.voiceagent.dashboard.api;

import com.vedryxtech.voiceagent.dashboard.domain.DashboardRange;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse;
import com.vedryxtech.voiceagent.dashboard.application.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** Read-only analytics for the dashboard UI. */
@Tag(name = "4. Dashboard",
        description = "The numbers and charts, all in one call, over the period you choose.")
@RestController
@RequestMapping(path = "/api/v1/dashboard", produces = "application/json")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * Everything the landing page needs, for one time window.
     *
     * <p>Leads are counted by when they arrived, calls by when they were placed, so the tiles
     * and the charts always describe the same period.</p>
     */
    @Operation(summary = "All dashboard numbers in one request",
            description = "Headline counts (done, still pending, converted), call quality (how often "
                    + "people pick up, average talk time), a breakdown for every status, and a "
                    + "day-by-day trend.\n\n"
                    + "Pick the period with `range`: **today**, **week** (7 days), "
                    + "**fifteenDays**, **month** (30 days), **quarter** (90 days), **all**, or "
                    + "**custom** with `from` and `to`. Defaults to month.\n\n"
                    + "Current status comes from the lead records; call history, outcomes and the "
                    + "trend come from the call log.")
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
    public DashboardSummaryResponse summary(
            @RequestParam(defaultValue = "month") DashboardRange range,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return dashboardService.summary(range, from, to);
    }
}
