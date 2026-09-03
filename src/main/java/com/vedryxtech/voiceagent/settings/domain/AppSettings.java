package com.vedryxtech.voiceagent.settings.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;

/**
 * Singleton document that holds every installation-wide setting: the calling policy the
 * dialler enforces, the timezone the calling window is expressed in, and the API-key hash
 * the voice agent authenticates with.
 *
 * <p>One row, always, at the fixed id {@link #SINGLETON_ID}. Bootstrap creates it on first
 * start if it is missing.</p>
 */
@Document(collection = "app_settings")
public class AppSettings {

    /** The one and only row. {@code current()} looks up by this id, so it is never wrong. */
    public static final String SINGLETON_ID = "singleton";

    @Id
    private String id = SINGLETON_ID;

    /** IANA zone used for calling windows and dashboard day buckets, e.g. {@code Asia/Kolkata}. */
    @Field("timezone")
    private String timezone = "Asia/Kolkata";

    /** The runtime-writable retry rules the dialler applies. Never null after bootstrap. */
    @Field("call_policy")
    private CallPolicy callPolicy = CallPolicy.defaults();

    // ------------------------------------------------------------------ API key
    //
    // Relocated off the old Organization document. Field names match what Organization used,
    // so a mongosh migration is just a $rename per field.

    /**
     * SHA-256 of the API key. The key itself is shown once, at creation, and never stored,
     * so a database dump does not hand out working credentials.
     */
    @Field("api_key_hash")
    private String apiKeyHash;

    /** First few characters, so the UI can show which key is in use without revealing it. */
    @Field("api_key_prefix")
    private String apiKeyPrefix;

    @Field("api_key_created_at")
    private OffsetDateTime apiKeyCreatedAt;

    @Field("api_key_last_used_at")
    private OffsetDateTime apiKeyLastUsedAt;

    @Field("created_at")
    private OffsetDateTime createdAt;

    @Field("updated_at")
    private OffsetDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public CallPolicy getCallPolicy() {
        return callPolicy;
    }

    public void setCallPolicy(CallPolicy callPolicy) {
        this.callPolicy = callPolicy;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public void setApiKeyHash(String apiKeyHash) {
        this.apiKeyHash = apiKeyHash;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public void setApiKeyPrefix(String apiKeyPrefix) {
        this.apiKeyPrefix = apiKeyPrefix;
    }

    public OffsetDateTime getApiKeyCreatedAt() {
        return apiKeyCreatedAt;
    }

    public void setApiKeyCreatedAt(OffsetDateTime apiKeyCreatedAt) {
        this.apiKeyCreatedAt = apiKeyCreatedAt;
    }

    public OffsetDateTime getApiKeyLastUsedAt() {
        return apiKeyLastUsedAt;
    }

    public void setApiKeyLastUsedAt(OffsetDateTime apiKeyLastUsedAt) {
        this.apiKeyLastUsedAt = apiKeyLastUsedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
