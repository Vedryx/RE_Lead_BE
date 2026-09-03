package com.vedryxtech.voiceagent.auth.persistence;

import com.vedryxtech.voiceagent.auth.domain.RefreshToken;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends MongoRepository<RefreshToken, ObjectId> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
