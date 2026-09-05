package com.vedryxtech.voiceagent.lead.domain;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * A lead in the {@code lead} collection: someone to phone.
 *
 * <p>A lead arrives <b>fresh</b> - a name and a number, nothing more - and is queued for a call
 * straight away. Everything below the "filled in by the call" line is empty until the agent has
 * actually spoken to them and reported the outcome. That is why {@code actionType},
 * {@code status} and the appointment times are all nullable: before the first call there is
 * nothing to put in them.</p>
 *
 * <p>The Mongo {@code _id} is the only identifier. It is generated here, so callers do not have
 * to supply one.</p>
 *
 * <p>This document holds only the <em>current</em> state. Every call attempt and every status
 * change is written to {@code leads_log} ({@link LeadCallLog}).</p>
 */
@Document(collection = "lead")
@CompoundIndexes({
        @CompoundIndex(name = "idx_pipeline_status", def = "{'pipeline_status': 1}"),
        @CompoundIndex(name = "idx_final_status", def = "{'final_status': 1}"),
        @CompoundIndex(name = "idx_action_status", def = "{'action_type': 1, 'status': 1}"),
        @CompoundIndex(name = "idx_created_at", def = "{'created_at': -1}"),
        // The dialler's claim query: due leads, oldest first.
        @CompoundIndex(name = "idx_dialer_queue", def = "{'pipeline_status': 1, 'next_attempt_at': 1}"),
        @CompoundIndex(name = "idx_scheduled_for", def = "{'scheduled_for': 1}"),
        @CompoundIndex(name = "idx_callback_at", def = "{'callback_at': 1}")
})
public class Lead {

    /** The only identifier. Generated on insert and returned to clients as the hex string {@code id}. */
    @Id
    private ObjectId id;

    @Field("created_at")
    private OffsetDateTime createdAt;

    @Field("updated_at")
    private OffsetDateTime updatedAt;

    // ------------------------------------------------- known before the call

    @Field("name")
    private String name;

    /** Number the lead was reached on. Kept in sync with {@link #callingPhone}. */
    @Indexed(name = "idx_phone")
    @Field("phone")
    private String phone;

    /** Business key: one lead per phone number. */
    @Indexed(name = "uk_calling_phone", unique = true)
    @Field("calling_phone")
    private String callingPhone;

    @Field("project")
    private String project;

    @Field("source")
    private String source;

    @Field("campaign")
    private String campaign;

    /** User id this lead is assigned to, when a human is working it. */
    @Field("assigned_to")
    private String assignedTo;

    // ---------------------------------------------------- the calling pipeline

    /**
     * Where this lead sits in the dialling pipeline. A fresh lead is {@code new} and due
     * immediately. Owned by the call APIs, not by CRUD.
     */
    @Field("pipeline_status")
    private LeadPipelineStatus pipelineStatus = LeadPipelineStatus.NEW;

    /** Set once the lead is closed. Null while it is still being worked. */
    @Field("final_status")
    private LeadFinalStatus finalStatus;

    /**
     * How far along the lead is, in sales terms. Derived from the same transition that
     * sets pipelineStatus and finalStatus; never written independently. See LeadStage.
     */
    @Indexed(name = "idx_stage")
    @Field("stage")
    private LeadStage stage = LeadStage.NEW;

    /** Result of the most recent connected call. */
    @Field("last_disposition")
    private CallDisposition lastDisposition;

    /** Telephony result of the most recent attempt, connected or not. */
    @Field("last_outcome")
    private CallOutcome lastOutcome;

    @Field("attempt_count")
    private Integer attemptCount = 0;

    /** How many attempts actually connected. Drives the dashboard's connect rate. */
    @Field("connected_count")
    private Integer connectedCount = 0;

    @Field("last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Field("last_connected_at")
    private OffsetDateTime lastConnectedAt;

    /** When the dialler may pick this lead up again. Null means "not queued". */
    @Field("next_attempt_at")
    private OffsetDateTime nextAttemptAt;

    @Field("total_talk_seconds")
    private Integer totalTalkSeconds = 0;

    /** {@code _id} of the newest {@code leads_log} entry, for a one-hop lookup. */
    @Field("last_call_log_id")
    private ObjectId lastCallLogId;

    /** Hard suppression: set by a DO_NOT_CALL disposition and never cleared by the dialler. */
    /**
     * The lead who gave us this name. Set only on referrals, and the reason a
     * referral is worth more than a cold number: somebody vouched for it.
     */
    @Field("referred_by_lead_id")
    private ObjectId referredByLeadId;

    /**
     * What the referrer said about this person, in their own call's words: what they are
     * looking for, and who is vouching for them.
     *
     * <p>This is the whole point of a referral. Without it the agent opens a cold call to a
     * stranger who never enquired; with it, it can open by naming who passed the number on
     * and what they were told this person wants.</p>
     */
    @Field("referral_summary")
    private String referralSummary;

    @Field("do_not_call")
    private Boolean doNotCall = Boolean.FALSE;

    // ------------------------------------------------- filled in by the call

    /** What was agreed on the call. Null until the lead has actually been spoken to. */
    @Field("action_type")
    private ActionType actionType;

    /** Status of that agreed action (scheduled, completed, cancelled...). Null until there is one. */
    @Field("status")
    private LeadStatus status;

    /** Set when the call ends in {@link ActionType#TEAM_CALLBACK}. */
    @Field("callback_at")
    private OffsetDateTime callbackAt;

    /** Set for {@link ActionType#SITE_VISIT} and {@link ActionType#FOLLOW_UP_CALL}. */
    @Field("scheduled_for")
    private OffsetDateTime scheduledFor;

    @Field("reminder_due_at")
    private OffsetDateTime reminderDueAt;

    @Field("reminder_enabled")
    private Boolean reminderEnabled;

    @Field("confirmed_by_lead")
    private Boolean confirmedByLead;

    @Field("whatsapp_phone")
    private String whatsappPhone;

    /** What the lead asked about. Usually captured during the call. */
    @Field("query")
    private String query;

    @Field("notes")
    private String notes;

    /** Playback URL of the most recent recording. Full history lives in {@code leads_log}. */
    @Field("last_recording_url")
    private String lastRecordingUrl;

    @Version
    @Field("version")
    private Long version;

    // ------------------------------------------------------------------ helpers

    public String getIdAsString() {
        return id == null ? null : id.toHexString();
    }

    public int attemptCountOrZero() {
        return attemptCount == null ? 0 : attemptCount;
    }

    public boolean isDoNotCall() {
        return Boolean.TRUE.equals(doNotCall);
    }

    public boolean isClosed() {
        return finalStatus != null || (pipelineStatus != null && pipelineStatus.isTerminal());
    }

    /** True until the first call has been reported: no action has been agreed yet. */
    public boolean isFresh() {
        return actionType == null && attemptCountOrZero() == 0;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCallingPhone() {
        return callingPhone;
    }

    public void setCallingPhone(String callingPhone) {
        this.callingPhone = callingPhone;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCampaign() {
        return campaign;
    }

    public void setCampaign(String campaign) {
        this.campaign = campaign;
    }

    public String getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(String assignedTo) {
        this.assignedTo = assignedTo;
    }

    public LeadPipelineStatus getPipelineStatus() {
        return pipelineStatus;
    }

    public void setPipelineStatus(LeadPipelineStatus pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public LeadFinalStatus getFinalStatus() {
        return finalStatus;
    }

    public LeadStage getStage() {
        return stage;
    }

    public void setStage(LeadStage stage) {
        this.stage = stage;
    }

    /** The current stage, defaulting for rows written before the field existed. */
    public LeadStage stageOrNew() {
        return stage == null ? LeadStage.NEW : stage;
    }

    public void setFinalStatus(LeadFinalStatus finalStatus) {
        this.finalStatus = finalStatus;
    }

    public CallDisposition getLastDisposition() {
        return lastDisposition;
    }

    public void setLastDisposition(CallDisposition lastDisposition) {
        this.lastDisposition = lastDisposition;
    }

    public CallOutcome getLastOutcome() {
        return lastOutcome;
    }

    public void setLastOutcome(CallOutcome lastOutcome) {
        this.lastOutcome = lastOutcome;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public Integer getConnectedCount() {
        return connectedCount;
    }

    public void setConnectedCount(Integer connectedCount) {
        this.connectedCount = connectedCount;
    }

    public OffsetDateTime getLastAttemptAt() {
        return lastAttemptAt;
    }

    public void setLastAttemptAt(OffsetDateTime lastAttemptAt) {
        this.lastAttemptAt = lastAttemptAt;
    }

    public OffsetDateTime getLastConnectedAt() {
        return lastConnectedAt;
    }

    public void setLastConnectedAt(OffsetDateTime lastConnectedAt) {
        this.lastConnectedAt = lastConnectedAt;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(OffsetDateTime nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Integer getTotalTalkSeconds() {
        return totalTalkSeconds;
    }

    public void setTotalTalkSeconds(Integer totalTalkSeconds) {
        this.totalTalkSeconds = totalTalkSeconds;
    }

    public ObjectId getLastCallLogId() {
        return lastCallLogId;
    }

    public void setLastCallLogId(ObjectId lastCallLogId) {
        this.lastCallLogId = lastCallLogId;
    }

    public ObjectId getReferredByLeadId() {
        return referredByLeadId;
    }

    public void setReferredByLeadId(ObjectId referredByLeadId) {
        this.referredByLeadId = referredByLeadId;
    }

    public String getReferralSummary() {
        return referralSummary;
    }

    public void setReferralSummary(String referralSummary) {
        this.referralSummary = referralSummary;
    }

    public Boolean getDoNotCall() {
        return doNotCall;
    }

    public void setDoNotCall(Boolean doNotCall) {
        this.doNotCall = doNotCall;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCallbackAt() {
        return callbackAt;
    }

    public void setCallbackAt(OffsetDateTime callbackAt) {
        this.callbackAt = callbackAt;
    }

    public OffsetDateTime getScheduledFor() {
        return scheduledFor;
    }

    public void setScheduledFor(OffsetDateTime scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public OffsetDateTime getReminderDueAt() {
        return reminderDueAt;
    }

    public void setReminderDueAt(OffsetDateTime reminderDueAt) {
        this.reminderDueAt = reminderDueAt;
    }

    public Boolean getReminderEnabled() {
        return reminderEnabled;
    }

    public void setReminderEnabled(Boolean reminderEnabled) {
        this.reminderEnabled = reminderEnabled;
    }

    public Boolean getConfirmedByLead() {
        return confirmedByLead;
    }

    public void setConfirmedByLead(Boolean confirmedByLead) {
        this.confirmedByLead = confirmedByLead;
    }

    public String getWhatsappPhone() {
        return whatsappPhone;
    }

    public void setWhatsappPhone(String whatsappPhone) {
        this.whatsappPhone = whatsappPhone;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getLastRecordingUrl() {
        return lastRecordingUrl;
    }

    public void setLastRecordingUrl(String lastRecordingUrl) {
        this.lastRecordingUrl = lastRecordingUrl;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Lead lead)) {
            return false;
        }
        return id != null && Objects.equals(id, lead.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Lead{id=" + id + ", name='" + name + "', callingPhone='" + callingPhone
                + "', pipelineStatus=" + pipelineStatus + ", actionType=" + actionType + "}";
    }
}
