package com.vedryxtech.voiceagent.lead.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * One manual edit to a lead: who, when, and what actually changed.
 *
 * <p>Written only when something a person decides changes — a stage, an owner, a
 * phone number, a do-not-call flag. Agent writes go through the call outcome path
 * and are already recorded on the call log; duplicating them here would bury the
 * human decisions in machine noise.
 *
 * <p>A separate collection rather than a field on the lead: this grows without
 * bound and is read rarely, and a lead document that carries its own history gets
 * slower for every read that does not want it.
 */
@Document(collection = "lead_audit")
@CompoundIndexes({
        @CompoundIndex(name = "idx_audit_lead_at", def = "{'lead_id': 1, 'at': -1}")
})
public class LeadAuditEntry {

    @Id
    private ObjectId id;

    @Field("lead_id")
    private ObjectId leadId;

    @Field("at")
    private OffsetDateTime at;

    /** The user id from the JWT, or {@code ai_agent} when there is no human. */
    @Field("actor")
    private String actor;

    /** The actor's email, so the trail reads without a join against the user collection. */
    @Field("actor_email")
    private String actorEmail;

    /** Which endpoint made the change: {@code patch}, {@code replace} or {@code upsert}. */
    @Field("via")
    private String via;

    @Field("changes")
    private List<FieldChange> changes;

    public LeadAuditEntry() {
    }

    public LeadAuditEntry(ObjectId leadId, OffsetDateTime at, String actor, String actorEmail,
                          String via, List<FieldChange> changes) {
        this.leadId = leadId;
        this.at = at;
        this.actor = actor;
        this.actorEmail = actorEmail;
        this.via = via;
        this.changes = changes;
    }

    /** One field, before and after, rendered as text so any type reads the same way. */
    public record FieldChange(String field, String from, String to) {
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getIdAsString() {
        return id == null ? null : id.toHexString();
    }

    public ObjectId getLeadId() {
        return leadId;
    }

    public void setLeadId(ObjectId leadId) {
        this.leadId = leadId;
    }

    public OffsetDateTime getAt() {
        return at;
    }

    public void setAt(OffsetDateTime at) {
        this.at = at;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }

    public List<FieldChange> getChanges() {
        return changes;
    }

    public void setChanges(List<FieldChange> changes) {
        this.changes = changes;
    }
}
