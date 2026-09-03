package com.vedryxtech.voiceagent.settings.application;

import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;

/**
 * Reads and writes the single {@link AppSettings} document. Replaces the tenant-scoped
 * {@code OrganizationService}: this installation has one policy, one API key and one timezone.
 */
public interface SettingsService {

    /** Loads the singleton, creating it with defaults if absent. Never returns null. */
    AppSettings current();

    /** The calling rules in force, falling back to built-in defaults. */
    CallPolicy currentPolicy();

    /** Persist a fully-formed policy. Callers responsible for merging partial patches first. */
    AppSettings updateCallPolicy(CallPolicy policy);

    /**
     * Persists the whole settings document. Used by the api-key service when it rotates or
     * revokes the key without rewriting the policy.
     */
    AppSettings save(AppSettings settings);
}
