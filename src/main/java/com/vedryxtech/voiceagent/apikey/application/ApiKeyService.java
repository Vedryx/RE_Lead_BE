package com.vedryxtech.voiceagent.apikey.application;

/**
 * The API key the voice agent authenticates with. One installation, one key — the hash lives
 * on the {@code app_settings} singleton.
 *
 * <p>The plaintext key is shown once, at creation, and only its SHA-256 hash is stored. That
 * means a lost key cannot be recovered (rotate instead) and a database dump contains no
 * working credentials.</p>
 */
public interface ApiKeyService {

    /** Generates a new key, replacing any existing one. */
    GeneratedKey generate();

    /** Seeds a specific key on first boot so a fresh checkout can be tested immediately. */
    void seed(String plainKey);

    /**
     * Resolves a presented key back to a boolean. A match returns true; anything else
     * (missing, unknown, no key configured) returns false. Kept minimal so the filter's
     * only job is to check membership.
     */
    boolean matches(String plainKey);

    /** Removes the key, immediately locking the agent out. */
    void revoke();

    /** The full key, returned exactly once. */
    record GeneratedKey(String apiKey, String prefix) {
    }
}
