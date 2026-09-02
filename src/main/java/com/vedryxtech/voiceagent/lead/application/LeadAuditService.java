package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadAuditEntry;
import com.vedryxtech.voiceagent.lead.persistence.LeadAuditRepository;
import com.vedryxtech.voiceagent.security.CurrentActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Answers "who moved this lead, and when".
 *
 * <p>Until now nothing did. A lead could change owner, stage, phone number or
 * do-not-call status and the only trace was the new value.
 */
@Service
public class LeadAuditService {

    private static final Logger log = LoggerFactory.getLogger(LeadAuditService.class);

    /**
     * The fields whose change is a decision rather than a side effect.
     *
     * <p>Deliberately excludes the counters the dialler maintains — {@code attemptCount},
     * {@code nextAttemptAt} and the rest move on their own several times a day, and an
     * audit trail that records them buries the handful of entries anyone wants to read.
     */
    private static final Map<String, Function<Lead, Object>> AUDITED = auditedFields();

    private final LeadAuditRepository repository;
    private final CurrentActor currentActor;

    public LeadAuditService(LeadAuditRepository repository, CurrentActor currentActor) {
        this.repository = repository;
        this.currentActor = currentActor;
    }

    /** A snapshot to compare against once the write has been applied. */
    public Map<String, Object> snapshot(Lead lead) {
        Map<String, Object> values = new LinkedHashMap<>();
        AUDITED.forEach((field, reader) -> values.put(field, reader.apply(lead)));
        return values;
    }

    /**
     * Record what changed between the snapshot and the saved lead.
     *
     * <p>Never throws. Losing an audit entry is bad; failing the edit that produced it
     * because the audit write failed is worse.
     */
    public void record(Map<String, Object> before, Lead after, String via) {
        try {
            List<LeadAuditEntry.FieldChange> changes = diff(before, after);
            if (changes.isEmpty()) {
                return;
            }
            repository.save(new LeadAuditEntry(after.getId(), OffsetDateTime.now(ZoneOffset.UTC),
                    currentActor.actor(), currentActor.email().orElse(null), via, changes));
        } catch (RuntimeException ex) {
            log.error("Could not record the audit entry for lead {}: {}",
                    after.getIdAsString(), ex.getMessage());
        }
    }

    public Page<LeadAuditEntry> history(Lead lead, Pageable pageable) {
        return repository.findByLeadIdOrderByAtDesc(lead.getId(), pageable);
    }

    private static List<LeadAuditEntry.FieldChange> diff(Map<String, Object> before, Lead after) {
        List<LeadAuditEntry.FieldChange> changes = new ArrayList<>();
        AUDITED.forEach((field, reader) -> {
            Object was = before.get(field);
            Object now = reader.apply(after);
            if (!Objects.equals(was, now)) {
                changes.add(new LeadAuditEntry.FieldChange(field, text(was), text(now)));
            }
        });
        return changes;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Map<String, Function<Lead, Object>> auditedFields() {
        Map<String, Function<Lead, Object>> fields = new LinkedHashMap<>();
        fields.put("stage", Lead::getStage);
        fields.put("pipelineStatus", Lead::getPipelineStatus);
        fields.put("finalStatus", Lead::getFinalStatus);
        fields.put("doNotCall", Lead::getDoNotCall);
        fields.put("assignedTo", Lead::getAssignedTo);
        fields.put("name", Lead::getName);
        fields.put("phone", Lead::getPhone);
        fields.put("callingPhone", Lead::getCallingPhone);
        fields.put("whatsappPhone", Lead::getWhatsappPhone);
        fields.put("project", Lead::getProject);
        fields.put("actionType", Lead::getActionType);
        fields.put("status", Lead::getStatus);
        fields.put("scheduledFor", Lead::getScheduledFor);
        fields.put("callbackAt", Lead::getCallbackAt);
        return fields;
    }
}
