package com.vedryxtech.voiceagent.dashboard.application;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import com.vedryxtech.voiceagent.dashboard.domain.DashboardRange;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse.CallStats;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse.CountBucket;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse.DayBucket;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse.Totals;
import com.vedryxtech.voiceagent.dashboard.api.dto.DashboardSummaryResponse.Window;
import com.vedryxtech.voiceagent.exception.InvalidLeadPayloadException;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.aggregation.DateOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Aggregates the dashboard numbers in Mongo rather than in Java: each breakdown is a
 * {@code $match} plus a {@code $group}, covered by the indexes on the two collections.
 *
 * <p>Two sources, one rule. <b>Current status</b> comes from {@code lead}, filtered on
 * {@code created_at}. <b>History</b> comes from {@code leads_log}, filtered on
 * {@code dial_started_at}. The same window is applied to both, so the tiles and the charts
 * always describe the same period.</p>
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final int MAX_TREND_DAYS = 90;

    private final MongoTemplate mongoTemplate;
    private final SettingsService settingsService;

    public DashboardServiceImpl(MongoTemplate mongoTemplate, SettingsService settingsService) {
        this.mongoTemplate = mongoTemplate;
        this.settingsService = settingsService;
    }

    @Override
    public DashboardSummaryResponse summary(DashboardRange range, OffsetDateTime from, OffsetDateTime to) {
        AppSettings settings = settingsService.current();
        ZoneId zone = zoneOf(settings);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        DashboardRange effective = range == null ? DashboardRange.MONTH : range;
        Window window = resolveWindow(effective, from, to, zone, now);

        // Current status: read from the lead documents, by when the lead arrived.
        Criteria leadScope = windowed(new Criteria(), "created_at", window);
        // History: read from the call log, by when the call was actually placed.
        // Rows with no dial_started_at are system audit entries, not dialled attempts.
        Criteria attemptScope = windowed(Criteria.where("dial_started_at").ne(null),
                "dial_started_at", window);

        Map<String, Long> byStage = countBy(Lead.class, leadScope, "stage");
        Map<String, Long> byPipeline = countBy(Lead.class, leadScope, "pipeline_status");
        Map<String, Long> byFinal = countBy(Lead.class, leadScope, "final_status");
        Map<String, Long> byActionType = countBy(Lead.class, leadScope, "action_type");
        Map<String, Long> byDisposition = countBy(LeadCallLog.class, attemptScope, "disposition");
        Map<String, Long> byOutcome = countBy(LeadCallLog.class, attemptScope, "outcome");

        long totalLeads = mongoTemplate.count(new Query(leadScope), Lead.class);

        return new DashboardSummaryResponse(
                now,
                window,
                buildTotals(leadScope, totalLeads, byPipeline, byFinal, now),
                buildCallStats(attemptScope, byOutcome, zone, now),
                // The funnel as a salesperson reads it. pipelineStatus below is machine
                // state and answers a different question.
                buckets(LeadStage.values(), LeadStage::getValue, byStage, totalLeads),
                buckets(LeadPipelineStatus.values(), LeadPipelineStatus::getValue, byPipeline, totalLeads),
                buckets(LeadFinalStatus.values(), LeadFinalStatus::getValue, byFinal, totalLeads),
                actionTypeBuckets(byActionType, totalLeads),
                buckets(CallDisposition.values(), CallDisposition::getValue, byDisposition, sum(byDisposition)),
                buckets(CallOutcome.values(), CallOutcome::getValue, byOutcome, sum(byOutcome)),
                dailyTrend(attemptScope, zone, window));
    }

    // ----------------------------------------------------------------- window

    private Window resolveWindow(DashboardRange range, OffsetDateTime from, OffsetDateTime to,
                                 ZoneId zone, OffsetDateTime now) {
        if (range.isCustom()) {
            if (from == null) {
                throw new InvalidLeadPayloadException(
                        "from is required when range is custom (and to defaults to now)");
            }
            OffsetDateTime end = to != null ? to : now;
            if (!from.isBefore(end)) {
                throw new InvalidLeadPayloadException("from must be before to");
            }
            int days = (int) Math.max(1, ChronoUnit.DAYS.between(
                    from.atZoneSameInstant(zone).toLocalDate(),
                    end.atZoneSameInstant(zone).toLocalDate()) + 1);
            return new Window(range, from, end, days, zone.getId());
        }

        if (!range.isBounded()) {
            return new Window(range, null, now, 0, zone.getId());
        }

        // Whole days in the organization's own timezone, so "week" means the last 7 calendar
        // days there rather than a rolling 168 hours from whenever the request happened to land.
        OffsetDateTime start = now.atZoneSameInstant(zone)
                .toLocalDate()
                .minusDays(range.days() - 1L)
                .atStartOfDay(zone)
                .toOffsetDateTime();

        return new Window(range, start, now, range.days(), zone.getId());
    }

    /** Adds the window's date bounds to a filter, or leaves it alone for {@code all}. */
    private Criteria windowed(Criteria base, String field, Window window) {
        if (window.from() == null) {
            return base;
        }
        return new Criteria().andOperator(
                base,
                Criteria.where(field)
                        .gte(Date.from(window.from().toInstant()))
                        .lte(Date.from(window.to().toInstant())));
    }

    // ------------------------------------------------------------------ tiles

    private Totals buildTotals(Criteria leadScope, long totalLeads, Map<String, Long> byPipeline,
                               Map<String, Long> byFinal, OffsetDateTime now) {
        long pending = Arrays.stream(LeadPipelineStatus.values())
                .filter(LeadPipelineStatus::isPending)
                .mapToLong(status -> byPipeline.getOrDefault(status.getValue(), 0L))
                .sum();
        long inProgress = Arrays.stream(LeadPipelineStatus.values())
                .filter(LeadPipelineStatus::isActive)
                .mapToLong(status -> byPipeline.getOrDefault(status.getValue(), 0L))
                .sum();
        long converted = Arrays.stream(LeadFinalStatus.values())
                .filter(LeadFinalStatus::isWon)
                .mapToLong(status -> byFinal.getOrDefault(status.getValue(), 0L))
                .sum();

        Query dueQuery = new Query(new Criteria().andOperator(
                leadScope,
                Criteria.where("do_not_call").ne(Boolean.TRUE),
                Criteria.where("pipeline_status").in(List.of(
                        LeadPipelineStatus.NEW.getValue(),
                        LeadPipelineStatus.QUEUED.getValue(),
                        LeadPipelineStatus.RETRY_SCHEDULED.getValue(),
                        LeadPipelineStatus.CALLBACK_SCHEDULED.getValue())),
                Criteria.where("next_attempt_at").lte(Date.from(now.toInstant()))));

        Query dncQuery = new Query(new Criteria().andOperator(
                leadScope, Criteria.where("do_not_call").is(Boolean.TRUE)));

        return new Totals(
                totalLeads,
                pending,
                inProgress,
                byPipeline.getOrDefault(LeadPipelineStatus.COMPLETED.getValue(), 0L),
                byPipeline.getOrDefault(LeadPipelineStatus.EXHAUSTED.getValue(), 0L),
                byPipeline.getOrDefault(LeadPipelineStatus.SUPPRESSED.getValue(), 0L),
                byPipeline.getOrDefault(LeadPipelineStatus.FAILED.getValue(), 0L),
                converted,
                mongoTemplate.count(dueQuery, Lead.class),
                mongoTemplate.count(dncQuery, Lead.class));
    }

    private CallStats buildCallStats(Criteria attemptScope, Map<String, Long> byOutcome,
                                     ZoneId zone, OffsetDateTime now) {
        long totalAttempts = mongoTemplate.count(new Query(attemptScope), LeadCallLog.class);
        long connected = byOutcome.getOrDefault(CallOutcome.ANSWERED.getValue(), 0L);

        Aggregation talkAgg = Aggregation.newAggregation(
                Aggregation.match(attemptScope),
                Aggregation.group().sum("talk_seconds").as("total"));
        AggregationResults<Document> talkResults =
                mongoTemplate.aggregate(talkAgg, LeadCallLog.class, Document.class);
        long totalTalkSeconds = talkResults.getMappedResults().isEmpty()
                ? 0L
                : asLong(talkResults.getMappedResults().get(0).get("total"));

        // "Today" is always today, whatever window the rest of the page is showing.
        Date startOfToday = Date.from(now.atZoneSameInstant(zone)
                .toLocalDate().atStartOfDay(zone).toInstant());

        long attemptsToday = mongoTemplate.count(new Query(new Criteria().andOperator(
                attemptScope, Criteria.where("dial_started_at").gte(startOfToday))), LeadCallLog.class);
        long connectedToday = mongoTemplate.count(new Query(new Criteria().andOperator(
                        attemptScope,
                        Criteria.where("dial_started_at").gte(startOfToday),
                        Criteria.where("outcome").is(CallOutcome.ANSWERED.getValue()))),
                LeadCallLog.class);
        long recordingsAvailable = mongoTemplate.count(new Query(new Criteria().andOperator(
                        attemptScope,
                        Criteria.where("recording_status").is(RecordingStatus.AVAILABLE.getValue()))),
                LeadCallLog.class);

        double connectRate = totalAttempts == 0 ? 0d : round(connected * 100d / totalAttempts);
        double averageTalk = connected == 0 ? 0d : round((double) totalTalkSeconds / connected);

        return new CallStats(totalAttempts, connected, totalAttempts - connected, connectRate,
                totalTalkSeconds, averageTalk, recordingsAvailable, attemptsToday, connectedToday);
    }

    // ------------------------------------------------------------------ trend

    private List<DayBucket> dailyTrend(Criteria attemptScope, ZoneId zone, Window window) {
        int days = window.days() > 0
                ? Math.min(window.days(), MAX_TREND_DAYS)
                : MAX_TREND_DAYS;

        LocalDate lastDay = window.to().atZoneSameInstant(zone).toLocalDate();
        LocalDate firstDay = window.from() != null
                ? window.from().atZoneSameInstant(zone).toLocalDate()
                : lastDay.minusDays(days - 1L);
        if (ChronoUnit.DAYS.between(firstDay, lastDay) + 1 > days) {
            firstDay = lastDay.minusDays(days - 1L);
        }

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(new Criteria().andOperator(
                        attemptScope,
                        Criteria.where("dial_started_at")
                                .gte(Date.from(firstDay.atStartOfDay(zone).toInstant())))),
                Aggregation.project()
                        .and(DateOperators.dateOf("dial_started_at")
                                .withTimezone(DateOperators.Timezone.valueOf(zone.getId()))
                                .toString("%Y-%m-%d")).as("day")
                        .and("talk_seconds").as("talk_seconds")
                        .and("outcome").as("outcome"),
                Aggregation.group("day")
                        .count().as("attempts")
                        .sum("talk_seconds").as("talk_seconds")
                        .sum(ConditionalOperators
                                .when(Criteria.where("outcome").is(CallOutcome.ANSWERED.getValue()))
                                .then(1).otherwise(0)).as("connected"),
                Aggregation.sort(org.springframework.data.domain.Sort.Direction.ASC, "_id"));

        Map<LocalDate, DayBucket> found = new LinkedHashMap<>();
        for (Document row : mongoTemplate.aggregate(aggregation, LeadCallLog.class, Document.class)) {
            String day = row.getString("_id");
            if (day == null) {
                continue;
            }
            LocalDate date = LocalDate.parse(day);
            found.put(date, new DayBucket(date,
                    asLong(row.get("attempts")),
                    asLong(row.get("connected")),
                    asLong(row.get("talk_seconds"))));
        }

        // Fill the gaps so the chart has one point per day, not a ragged series.
        List<DayBucket> trend = new ArrayList<>();
        for (LocalDate date = firstDay; !date.isAfter(lastDay); date = date.plusDays(1)) {
            trend.add(found.getOrDefault(date, new DayBucket(date, 0L, 0L, 0L)));
        }
        return trend;
    }

    // ---------------------------------------------------------------- helpers

    /** {@code $match} on the window then {@code $group} by one field, as value -> count. */
    private Map<String, Long> countBy(Class<?> entity, Criteria scope, String field) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(scope),
                Aggregation.group(field).count().as("count"));

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Document row : mongoTemplate.aggregate(aggregation, entity, Document.class)) {
            Object key = row.get("_id");
            if (key != null) {
                counts.put(key.toString(), asLong(row.get("count")));
            }
        }
        return counts;
    }

    private <E extends Enum<E>> List<CountBucket> buckets(E[] values, Function<E, String> keyOf,
                                                          Map<String, Long> counts, long total) {
        List<CountBucket> buckets = new ArrayList<>(values.length);
        for (E value : values) {
            String key = keyOf.apply(value);
            long count = counts.getOrDefault(key, 0L);
            buckets.add(new CountBucket(key, humanize(key), count, percentage(count, total)));
        }
        return buckets;
    }

    private List<CountBucket> actionTypeBuckets(Map<String, Long> counts, long total) {
        List<CountBucket> buckets = new ArrayList<>();
        counts.forEach((key, count) ->
                buckets.add(new CountBucket(key, humanize(key), count, percentage(count, total))));
        buckets.sort((left, right) -> Long.compare(right.count(), left.count()));
        return buckets;
    }

    private static double percentage(long count, long total) {
        return total <= 0 ? 0d : round(count * 100d / total);
    }

    private static double round(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static long sum(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    /** {@code retryScheduled} becomes {@code Retry scheduled} for the chart legend. */
    private static String humanize(String wireValue) {
        String spaced = wireValue.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1).toLowerCase(Locale.ROOT);
    }

    private ZoneId zoneOf(AppSettings settings) {
        try {
            return settings.getTimezone() == null || settings.getTimezone().isBlank()
                    ? ZoneId.of("Asia/Kolkata")
                    : ZoneId.of(settings.getTimezone());
        } catch (RuntimeException ex) {
            return ZoneId.of("Asia/Kolkata");
        }
    }
}
