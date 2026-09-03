package com.vedryxtech.voiceagent.auth.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;

/**
 * Server-side refresh token. The token itself is never stored; only its SHA-256 hash is,
 * so a database dump does not hand out live sessions.
 *
 * <p>One-time use: {@link #rotatedTo} points at the successor issued on the swap. Reusing
 * an already-rotated token is treated as a replay attack — the presented token must exist,
 * not be revoked, and not be past its {@link #expiresAt}.</p>
 */
@Document(collection = "refresh_token")
public class RefreshToken {

    @Id
    private ObjectId id;

    /** SHA-256 hex of the plaintext token. Indexed for O(1) lookup on refresh. */
    @Indexed(name = "uk_refresh_token_hash", unique = true)
    @Field("token_hash")
    private String tokenHash;

    @Indexed(name = "idx_refresh_user")
    @Field("user_id")
    private String userId;

    @Field("expires_at")
    private OffsetDateTime expiresAt;

    @Field("created_at")
    private OffsetDateTime createdAt;

    @Field("revoked_at")
    private OffsetDateTime revokedAt;

    /** The successor token issued when this one was rotated on a refresh. Null until rotated. */
    @Field("rotated_to")
    private String rotatedTo;

    public String getIdAsString() {
        return id == null ? null : id.toHexString();
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(OffsetDateTime now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isRotated() {
        return rotatedTo != null;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(OffsetDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(OffsetDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public String getRotatedTo() {
        return rotatedTo;
    }

    public void setRotatedTo(String rotatedTo) {
        this.rotatedTo = rotatedTo;
    }
}
