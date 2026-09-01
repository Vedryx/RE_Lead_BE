package com.vedryxtech.voiceagent.organization.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;

/**
 * The company running the dashboard. Users are created under it, and it owns the API key the
 * AI voice agent authenticates with.
 */
@Document(collection = "organization")
public class Organization {

    @Id
    private ObjectId id;

    @Field("name")
    private String name;

    /** URL-safe unique handle, e.g. {@code my-home-sanctuary}. */
    @Indexed(name = "uk_org_slug", unique = true)
    @Field("slug")
    private String slug;

    @Field("status")
    private OrganizationStatus status;

    @Field("contact_email")
    private String contactEmail;

    @Field("contact_phone")
    private String contactPhone;

    /** IANA zone used for calling windows and dashboard day buckets, e.g. {@code Asia/Kolkata}. */
    @Field("timezone")
    private String timezone;

    @Field("call_policy")
    private CallPolicy callPolicy;

    // ------------------------------------------------------------------ API key

    /**
     * SHA-256 of the API key. The key itself is shown once, at creation, and never stored,
     * so a database dump does not hand out working credentials.
     */
    @Indexed(name = "uk_org_api_key_hash", unique = true, sparse = true)
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

    public String getIdAsString() {
        return id == null ? null : id.toHexString();
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public void setStatus(OrganizationStatus status) {
        this.status = status;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
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
