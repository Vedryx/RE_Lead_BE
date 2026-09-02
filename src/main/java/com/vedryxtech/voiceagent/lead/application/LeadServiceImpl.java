package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStatus;
import com.vedryxtech.voiceagent.lead.api.dto.LeadPatchRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import com.vedryxtech.voiceagent.exception.DuplicateResourceException;
import com.vedryxtech.voiceagent.exception.InvalidLeadPayloadException;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.lead.mapper.LeadMapper;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import com.vedryxtech.voiceagent.lead.application.LeadSearchCriteria;
import com.vedryxtech.voiceagent.lead.application.LeadService;
import com.vedryxtech.voiceagent.common.util.PhoneNumbers;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.util.regex.Pattern;

@Service
public class LeadServiceImpl implements LeadService {

    /** Matches the agent convention of reminding 30 minutes ahead of the appointment. */
    private static final Duration DEFAULT_REMINDER_LEAD_TIME = Duration.ofMinutes(30);

    private final LeadRepository repository;
    private final MongoTemplate mongoTemplate;
    private final LeadMapper mapper;
    private final LeadAuditService audit;

    public LeadServiceImpl(LeadRepository repository, MongoTemplate mongoTemplate, LeadMapper mapper,
                           LeadAuditService audit) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
        this.mapper = mapper;
        this.audit = audit;
    }

    // ------------------------------------------------------------------ create

    @Override
    public Lead create(LeadRequest request) {
        Lead lead = mapper.toEntity(request);
        applyDefaults(lead);
        validate(lead);

        if (repository.existsByCallingPhone(lead.getCallingPhone())) {
            throw DuplicateResourceException.callingPhone(lead.getCallingPhone());
        }
        return persist(lead);
    }

    @Override
    public UpsertResult upsert(LeadRequest request) {
        String callingPhone = PhoneNumbers.normalize(
                request.callingPhone() != null ? request.callingPhone() : request.phone());

        Optional<Lead> existing = repository.findByCallingPhone(callingPhone);
        if (existing.isEmpty()) {
            return new UpsertResult(create(request), true);
        }

        Lead lead = existing.get();
        Map<String, Object> before = audit.snapshot(lead);
        mapper.applyFullUpdate(lead, request);
        applyDefaults(lead);
        validate(lead);
        Lead saved = persist(lead);
        audit.record(before, saved, "upsert");
        return new UpsertResult(saved, false);
    }

    // -------------------------------------------------------------------- read

    @Override
    public Lead getById(String id) {
        return repository.findById(toObjectId(id))
                .orElseThrow(() -> ResourceNotFoundException.lead("id", id));
    }

    @Override
    public Page<Lead> search(LeadSearchCriteria criteria, Pageable pageable) {
        Query query = new Query();
        List<Criteria> filters = new ArrayList<>();

        if (hasText(criteria.project())) {
            filters.add(Criteria.where("project").is(criteria.project().trim()));
        }
        if (criteria.actionType() != null) {
            filters.add(Criteria.where("action_type").is(criteria.actionType().getValue()));
        }
        if (criteria.status() != null) {
            filters.add(Criteria.where("status").is(criteria.status().getValue()));
        }
        if (criteria.pipelineStatus() != null) {
            filters.add(Criteria.where("pipeline_status").is(criteria.pipelineStatus().getValue()));
        }
        if (criteria.callbackBefore() != null) {
            // "Waiting on a person": a callback whose time has come and gone. Invisible
            // to the agent by design, and invisible to everyone else without this.
            filters.add(Criteria.where("callback_at").lte(criteria.callbackBefore()));
        }
        if (criteria.stage() != null) {
            filters.add(Criteria.where("stage").is(criteria.stage().getValue()));
        }
        if (criteria.finalStatus() != null) {
            filters.add(Criteria.where("final_status").is(criteria.finalStatus().getValue()));
        }
        if (criteria.disposition() != null) {
            filters.add(Criteria.where("last_disposition").is(criteria.disposition().getValue()));
        }
        if (hasText(criteria.phone())) {
            String normalized = PhoneNumbers.normalize(criteria.phone());
            filters.add(new Criteria().orOperator(
                    Criteria.where("calling_phone").is(normalized),
                    Criteria.where("phone").is(normalized),
                    Criteria.where("whatsapp_phone").is(normalized)));
        }
        if (hasText(criteria.name())) {
            filters.add(Criteria.where("name").regex(Pattern.quote(criteria.name().trim()), "i"));
        }
        if (hasText(criteria.assignedTo())) {
            filters.add(Criteria.where("assigned_to").is(criteria.assignedTo().trim()));
        }
        if (criteria.confirmedByLead() != null) {
            filters.add(Criteria.where("confirmed_by_lead").is(criteria.confirmedByLead()));
        }
        if (criteria.hasRecording() != null) {
            filters.add(criteria.hasRecording()
                    ? Criteria.where("last_recording_url").ne(null)
                    : Criteria.where("last_recording_url").is(null));
        }
        addRange(filters, "created_at", criteria.createdFrom(), criteria.createdTo());
        addRange(filters, "scheduled_for", criteria.scheduledFrom(), criteria.scheduledTo());

        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        }

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), Lead.class);
        List<Lead> leads = mongoTemplate.find(query.with(pageable), Lead.class);
        return new PageImpl<>(leads, pageable, total);
    }

    // ------------------------------------------------------------------ update

    @Override
    public Lead replace(String id, LeadRequest request) {
        Lead lead = getById(id);
        Map<String, Object> before = audit.snapshot(lead);
        mapper.applyFullUpdate(lead, request);
        applyDefaults(lead);
        validate(lead);
        assertCallingPhoneFree(lead);
        Lead saved = persist(lead);
        audit.record(before, saved, "replace");
        return saved;
    }

    @Override
    public Lead patch(String id, LeadPatchRequest request) {
        Lead lead = getById(id);
        Map<String, Object> before = audit.snapshot(lead);
        boolean wasSuppressed = lead.isDoNotCall();
        mapper.applyPatch(lead, request);
        applyDefaults(lead);
        validate(lead);
        assertCallingPhoneFree(lead);
        LeadConsent.applyClearance(lead, wasSuppressed, request.doNotCallClearedReason());
        applySuppressionRules(lead);
        Lead saved = persist(lead);
        audit.record(before, saved, "patch");
        return saved;
    }


    // ----------------------------------------------------------------- helpers

    /** Fills in everything the agent may legitimately omit. */
    private void applyDefaults(Lead lead) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (lead.getCreatedAt() == null) {
            lead.setCreatedAt(now);
        }
        lead.setUpdatedAt(now);

        // A fresh lead has no action yet, so it has no action status either.
        if (lead.getActionType() != null && lead.getStatus() == null) {
            lead.setStatus(lead.getActionType() == ActionType.WHATSAPP_PROJECT_DETAILS
                    ? LeadStatus.REQUESTED
                    : LeadStatus.SCHEDULED);
        }
        if (lead.getPipelineStatus() == null) {
            lead.setPipelineStatus(LeadPipelineStatus.NEW);
        }
        // A brand new lead is immediately claimable; an in-flight one keeps its own schedule.
        if (lead.getNextAttemptAt() == null && lead.getPipelineStatus() == LeadPipelineStatus.NEW) {
            lead.setNextAttemptAt(now);
        }
        if (lead.getAttemptCount() == null) {
            lead.setAttemptCount(0);
        }
        if (lead.getConnectedCount() == null) {
            lead.setConnectedCount(0);
        }
        if (lead.getTotalTalkSeconds() == null) {
            lead.setTotalTalkSeconds(0);
        }
        if (lead.getDoNotCall() == null) {
            lead.setDoNotCall(Boolean.FALSE);
        }

        lead.setPhone(PhoneNumbers.normalize(lead.getPhone()));
        String callingPhone = PhoneNumbers.normalize(lead.getCallingPhone());
        lead.setCallingPhone(callingPhone != null ? callingPhone : lead.getPhone());

        if (lead.getActionType() == ActionType.WHATSAPP_PROJECT_DETAILS) {
            String whatsapp = PhoneNumbers.normalize(lead.getWhatsappPhone());
            lead.setWhatsappPhone(whatsapp != null ? whatsapp : lead.getPhone());
        } else {
            lead.setWhatsappPhone(PhoneNumbers.normalize(lead.getWhatsappPhone()));
        }

        if (lead.getActionType() != null && lead.getActionType().isCalendarBooking()) {
            if (lead.getReminderEnabled() == null) {
                lead.setReminderEnabled(Boolean.FALSE);
            }
            if (Boolean.TRUE.equals(lead.getReminderEnabled())
                    && lead.getReminderDueAt() == null
                    && lead.getScheduledFor() != null) {
                lead.setReminderDueAt(lead.getScheduledFor().minus(DEFAULT_REMINDER_LEAD_TIME));
            }
        }
    }

    /**
     * A fresh lead only needs a number to be callable. The action rules below apply once an
     * action has actually been agreed - normally when the call outcome is reported.
     */
    private void validate(Lead lead) {
        if (!hasText(lead.getCallingPhone())) {
            throw new InvalidLeadPayloadException("phone (or callingPhone) is required");
        }

        ActionType actionType = lead.getActionType();
        if (actionType == null) {
            return;
        }

        switch (actionType) {
            case TEAM_CALLBACK -> {
                if (lead.getCallbackAt() == null) {
                    throw new InvalidLeadPayloadException(
                            "callbackAt is required for actionType teamCallback");
                }
            }
            case SITE_VISIT, FOLLOW_UP_CALL -> {
                if (lead.getScheduledFor() == null) {
                    throw new InvalidLeadPayloadException(
                            "scheduledFor is required for actionType " + actionType.getValue());
                }
                if (lead.getReminderDueAt() != null
                        && !lead.getReminderDueAt().isBefore(lead.getScheduledFor())) {
                    throw new InvalidLeadPayloadException("reminderDueAt must be before scheduledFor");
                }
            }
            case WHATSAPP_PROJECT_DETAILS -> {
                if (!hasText(lead.getWhatsappPhone())) {
                    throw new InvalidLeadPayloadException(
                            "whatsappPhone is required for actionType whatsappProjectDetails");
                }
            }
        }
    }

    /** Guards the per-tenant unique calling phone when an update moves a lead to another number. */
    private void assertCallingPhoneFree(Lead lead) {
        repository.findByCallingPhone(lead.getCallingPhone())
                .filter(other -> !other.getId().equals(lead.getId()))
                .ifPresent(other -> {
                    throw DuplicateResourceException.callingPhone(lead.getCallingPhone());
                });
    }

    private void applySuppressionRules(Lead lead) {
        LeadConsent.applySuppression(lead);
    }

    private Lead persist(Lead lead) {
        try {
            return repository.save(lead);
        } catch (DuplicateKeyException ex) {
            // Backstop for the race between the pre-check above and the unique index.
            throw DuplicateResourceException.callingPhone(lead.getCallingPhone());
        }
    }

    private void addRange(List<Criteria> filters, String field, OffsetDateTime from, OffsetDateTime to) {
        if (from == null && to == null) {
            return;
        }
        Criteria criteria = Criteria.where(field);
        if (from != null) {
            criteria = criteria.gte(Date.from(from.toInstant()));
        }
        if (to != null) {
            criteria = criteria.lte(Date.from(to.toInstant()));
        }
        filters.add(criteria);
    }

    private ObjectId toObjectId(String id) {
        if (!ObjectId.isValid(id)) {
            throw ResourceNotFoundException.lead("id", id);
        }
        return new ObjectId(id);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
