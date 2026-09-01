package com.vedryxtech.voiceagent.lead.persistence;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeadRepository extends MongoRepository<Lead, ObjectId> {

    /** One lead per phone number; this is the de-duplication lookup. */
    Optional<Lead> findByCallingPhone(String callingPhone);

    boolean existsByCallingPhone(String callingPhone);
}
