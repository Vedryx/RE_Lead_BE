package com.vedryxtech.voiceagent.apikey.application;

import com.vedryxtech.voiceagent.organization.domain.Organization;

import java.util.Optional;

/**
 * API keys let the AI voice agent talk to this application without a human logging in.
 *
 * <p>The key is generated here, shown once, and only its SHA-256 hash is stored. That means a
 * lost key cannot be recovered - it is rotated instead - and a database dump contains no
 * working credentials.</p>
 */
public interface ApiKeyService {

    /** Generates a new key for the organization, replacing any existing one. */
    GeneratedKey generate();

    /** Seeds a specific key on first boot so a fresh checkout can be tested immediately. */
    void seed(Organization organization, String plainKey);

    /** Resolves a presented key back to its organization, or empty when it is not valid. */
    Optional<Organization> resolve(String plainKey);

    /** Removes the key, immediately locking the AI agent out. */
    void revoke();

    /** The full key, returned exactly once. After this only the prefix is ever visible. */
    record GeneratedKey(String apiKey, String prefix) {
    }
}
