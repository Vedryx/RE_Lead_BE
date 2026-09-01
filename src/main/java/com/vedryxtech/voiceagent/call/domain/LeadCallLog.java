package com.vedryxtech.voiceagent.call.domain;

import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * One document per call attempt, in the {@code leads_log} collection. Append-only: an attempt
 * is opened when the dialler picks the lead up and closed when the leg hangs up. The lead
 * document keeps only the current state; this collection keeps the whole history, which is
 * what the follow-up view, the recordings player and the dashboard read from.
 */
@Document(collection = "leads_log")
@CompoundIndexes({
        @CompoundIndex(name = "idx_log_created", def = "{'created_at': -1}"),
        @CompoundIndex(name = "idx_log_lead_attempt", def = "{'lead_id': 1, 'attempt_number': -1}"),
        @CompoundIndex(name = "idx_log_outcome", def = "{'outcome': 1}"),
        @CompoundIndex(name = "idx_log_disposition", def = "{'disposition': 1}"),
        @CompoundIndex(name = "idx_log_recording", def = "{'recording_status': 1}")
})
public class LeadCallLog {

    @Id
    private ObjectId id;

    /** The lead this attempt belongs to. */
    @Field("lead_id")
    private ObjectId leadId;

    @Field("phone")
    private String phone;

    @Field("name")
    private String name;

    @Field("project")
    private String project;

    /** 1-based; the nth time we have dialled this lead. */
    @Field("attempt_number")
    private Integer attemptNumber;

    /** Guards against a retried webhook or duplicate worker opening the same attempt twice. */
    @Indexed(name = "uk_log_idempotency", unique = true, sparse = true)
    @Field("idempotency_key")
    private String idempotencyKey;

    @Field("direction")
    private String direction = "outbound";

    /** {@code ai_agent} or a user id, so mixed AI/human calling stays attributable. */
    @Field("handled_by")
    private String handledBy;

    @Field("outcome")
    private CallOutcome outcome;

    @Field("disposition")
    private CallDisposition disposition;

    @Field("pipeline_status_before")
    private LeadPipelineStatus pipelineStatusBefore;

    @Field("pipeline_status_after")
    private LeadPipelineStatus pipelineStatusAfter;

    @Field("queued_at")
    private OffsetDateTime queuedAt;

    @Field("dial_started_at")
    private OffsetDateTime dialStartedAt;

    @Field("answered_at")
    private OffsetDateTime answeredAt;

    @Field("ended_at")
    private OffsetDateTime endedAt;

    /** Seconds spent ringing before answer or give-up. */
    @Field("ring_seconds")
    private Integer ringSeconds;

    /** Seconds of actual conversation. Zero for every unanswered attempt. */
    @Field("talk_seconds")
    private Integer talkSeconds;

    // -------------------------------------------------------------- recordings

    @Field("recording_status")
    private RecordingStatus recordingStatus = RecordingStatus.NOT_REQUESTED;

    /** Playback URL handed to the dashboard player. Attached when the outcome is reported. */
    @Field("recording_url")
    private String recordingUrl;

    @Field("recording_duration_seconds")
    private Integer recordingDurationSeconds;

    @Field("recording_size_bytes")
    private Long recordingSizeBytes;

    @Field("recording_ready_at")
    private OffsetDateTime recordingReadyAt;

    @Field("transcript_url")
    private String transcriptUrl;

    // ---------------------------------------------------------------- outcomes

    /** What the lead said, in one line, for the follow-up list. */
    @Field("summary")
    private String summary;

    @Field("notes")
    private String notes;

    /** Set when the lead asked to be called at a specific later time. */
    @Field("requested_callback_at")
    private OffsetDateTime requestedCallbackAt;

    /** When the dialler will try again; null once the lead is closed. */
    @Field("retry_scheduled_for")
    private OffsetDateTime retryScheduledFor;

    @Field("error_code")
    private String errorCode;

    @Field("error_message")
    private String errorMessage;

    @Field("events")
    private List<CallEvent> events = new ArrayList<>();

    @Field("created_at")
    private OffsetDateTime createdAt;

    @Field("updated_at")
    private OffsetDateTime updatedAt;

    public void addEvent(CallEvent event) {
        if (events == null) {
            events = new ArrayList<>();
        }
        events.add(event);
    }

    public String getIdAsString() {
        return id == null ? null : id.toHexString();
    }

    public boolean isClosed() {
        return outcome != null && endedAt != null;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public ObjectId getLeadId() {
        return leadId;
    }

    public void setLeadId(ObjectId leadId) {
        this.leadId = leadId;
    }

    public String getLeadIdAsString() {
        return leadId == null ? null : leadId.toHexString();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public void setHandledBy(String handledBy) {
        this.handledBy = handledBy;
    }

    public CallOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(CallOutcome outcome) {
        this.outcome = outcome;
    }

    public CallDisposition getDisposition() {
        return disposition;
    }

    public void setDisposition(CallDisposition disposition) {
        this.disposition = disposition;
    }

    public LeadPipelineStatus getPipelineStatusBefore() {
        return pipelineStatusBefore;
    }

    public void setPipelineStatusBefore(LeadPipelineStatus pipelineStatusBefore) {
        this.pipelineStatusBefore = pipelineStatusBefore;
    }

    public LeadPipelineStatus getPipelineStatusAfter() {
        return pipelineStatusAfter;
    }

    public void setPipelineStatusAfter(LeadPipelineStatus pipelineStatusAfter) {
        this.pipelineStatusAfter = pipelineStatusAfter;
    }

    public OffsetDateTime getQueuedAt() {
        return queuedAt;
    }

    public void setQueuedAt(OffsetDateTime queuedAt) {
        this.queuedAt = queuedAt;
    }

    public OffsetDateTime getDialStartedAt() {
        return dialStartedAt;
    }

    public void setDialStartedAt(OffsetDateTime dialStartedAt) {
        this.dialStartedAt = dialStartedAt;
    }

    public OffsetDateTime getAnsweredAt() {
        return answeredAt;
    }

    public void setAnsweredAt(OffsetDateTime answeredAt) {
        this.answeredAt = answeredAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public Integer getRingSeconds() {
        return ringSeconds;
    }

    public void setRingSeconds(Integer ringSeconds) {
        this.ringSeconds = ringSeconds;
    }

    public Integer getTalkSeconds() {
        return talkSeconds;
    }

    public void setTalkSeconds(Integer talkSeconds) {
        this.talkSeconds = talkSeconds;
    }

    public RecordingStatus getRecordingStatus() {
        return recordingStatus;
    }

    public void setRecordingStatus(RecordingStatus recordingStatus) {
        this.recordingStatus = recordingStatus;
    }

    public String getRecordingUrl() {
        return recordingUrl;
    }

    public void setRecordingUrl(String recordingUrl) {
        this.recordingUrl = recordingUrl;
    }

    public Integer getRecordingDurationSeconds() {
        return recordingDurationSeconds;
    }

    public void setRecordingDurationSeconds(Integer recordingDurationSeconds) {
        this.recordingDurationSeconds = recordingDurationSeconds;
    }

    public Long getRecordingSizeBytes() {
        return recordingSizeBytes;
    }

    public void setRecordingSizeBytes(Long recordingSizeBytes) {
        this.recordingSizeBytes = recordingSizeBytes;
    }

    public OffsetDateTime getRecordingReadyAt() {
        return recordingReadyAt;
    }

    public void setRecordingReadyAt(OffsetDateTime recordingReadyAt) {
        this.recordingReadyAt = recordingReadyAt;
    }

    public String getTranscriptUrl() {
        return transcriptUrl;
    }

    public void setTranscriptUrl(String transcriptUrl) {
        this.transcriptUrl = transcriptUrl;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getRequestedCallbackAt() {
        return requestedCallbackAt;
    }

    public void setRequestedCallbackAt(OffsetDateTime requestedCallbackAt) {
        this.requestedCallbackAt = requestedCallbackAt;
    }

    public OffsetDateTime getRetryScheduledFor() {
        return retryScheduledFor;
    }

    public void setRetryScheduledFor(OffsetDateTime retryScheduledFor) {
        this.retryScheduledFor = retryScheduledFor;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<CallEvent> getEvents() {
        return events;
    }

    public void setEvents(List<CallEvent> events) {
        this.events = events;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
