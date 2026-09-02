package com.vedryxtech.voiceagent.lead.persistence;

import com.vedryxtech.voiceagent.lead.domain.LeadAuditEntry;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface LeadAuditRepository extends MongoRepository<LeadAuditEntry, ObjectId> {

    Page<LeadAuditEntry> findByLeadIdOrderByAtDesc(ObjectId leadId, Pageable pageable);
}
