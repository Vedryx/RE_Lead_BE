package com.vedryxtech.voiceagent.settings.persistence;

import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * One row: the singleton at {@link AppSettings#SINGLETON_ID}. The api-key filter loads that
 * one row and compares hashes in memory, so no secondary index is needed.
 */
@Repository
public interface AppSettingsRepository extends MongoRepository<AppSettings, String> {
}
