package com.vedryxtech.voiceagent.organization.persistence;

import com.vedryxtech.voiceagent.organization.domain.Organization;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrganizationRepository extends MongoRepository<Organization, ObjectId> {

    Optional<Organization> findBySlug(String slug);

    boolean existsBySlug(String slug);

    /** Used by the API-key filter on every agent request, so it is indexed and unique. */
    Optional<Organization> findByApiKeyHash(String apiKeyHash);
}
