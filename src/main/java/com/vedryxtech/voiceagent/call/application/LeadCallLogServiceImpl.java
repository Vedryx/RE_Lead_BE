package com.vedryxtech.voiceagent.call.application;

import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.domain.RecordingStatus;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.call.persistence.LeadCallLogRepository;
import com.vedryxtech.voiceagent.common.util.PhoneNumbers;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class LeadCallLogServiceImpl implements LeadCallLogService {

    private final LeadCallLogRepository repository;
    private final MongoTemplate mongoTemplate;

    public LeadCallLogServiceImpl(LeadCallLogRepository repository, MongoTemplate mongoTemplate) {
        this.repository = repository;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public LeadCallLog require(String callLogId) {
        if (!ObjectId.isValid(callLogId)) {
            throw ResourceNotFoundException.callLog("id", callLogId);
        }
        return repository.findById(new ObjectId(callLogId))
                .orElseThrow(() -> ResourceNotFoundException.callLog("id", callLogId));
    }

    @Override
    public List<LeadCallLog> historyForLead(String leadId) {
        if (!ObjectId.isValid(leadId)) {
            throw ResourceNotFoundException.lead("id", leadId);
        }
        return repository.findByLeadIdOrderByAttemptNumberDesc(new ObjectId(leadId));
    }

    @Override
    public Page<LeadCallLog> search(CallLogSearchCriteria criteria, Pageable pageable) {
        List<Criteria> filters = new ArrayList<>();

        if (hasText(criteria.leadId()) && ObjectId.isValid(criteria.leadId().trim())) {
            filters.add(Criteria.where("lead_id").is(new ObjectId(criteria.leadId().trim())));
        }
        if (hasText(criteria.phone())) {
            filters.add(Criteria.where("phone").is(PhoneNumbers.normalize(criteria.phone())));
        }
        if (hasText(criteria.outcome())) {
            filters.add(Criteria.where("outcome").is(criteria.outcome().trim()));
        }
        if (hasText(criteria.disposition())) {
            filters.add(Criteria.where("disposition").is(criteria.disposition().trim()));
        }
        if (hasText(criteria.recordingStatus())) {
            filters.add(Criteria.where("recording_status").is(criteria.recordingStatus().trim()));
        }
        if (criteria.hasRecording() != null) {
            filters.add(criteria.hasRecording()
                    ? Criteria.where("recording_url").ne(null)
                    : Criteria.where("recording_url").is(null));
        }
        addRange(filters, "created_at", criteria.from(), criteria.to());

        Query query = filters.isEmpty()
                ? new Query()
                : new Query(new Criteria().andOperator(filters.toArray(new Criteria[0])));
        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), LeadCallLog.class);
        List<LeadCallLog> logs = mongoTemplate.find(query.with(pageable), LeadCallLog.class);
        return new PageImpl<>(logs, pageable, total);
    }

    @Override
    public Page<LeadCallLog> recordings(Pageable pageable) {
        Query query = new Query(Criteria.where("recording_status").is(RecordingStatus.AVAILABLE.getValue())
                .and("recording_url").ne(null));

        long total = mongoTemplate.count(Query.of(query).limit(-1).skip(-1), LeadCallLog.class);
        List<LeadCallLog> logs = mongoTemplate.find(query.with(pageable), LeadCallLog.class);
        return new PageImpl<>(logs, pageable, total);
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
