package com.vedryxtech.voiceagent.user.domain;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A dashboard login. Single-tenant: every user belongs to this installation, so there is no
 * {@code organization_id} scoping any more. Existing documents still carrying that field
 * deserialise fine — the value is simply ignored.
 */
@Document(collection = "app_user")
public class User {

    @Id
    private ObjectId id;

    @Indexed(name = "uk_user_email", unique = true)
    @Field("email")
    private String email;

    /** BCrypt hash. Never serialized to the API. */
    @Field("password_hash")
    private String passwordHash;

    @Field("full_name")
    private String fullName;

    @Field("phone")
    private String phone;

    @Field("roles")
    private Set<UserRole> roles = new LinkedHashSet<>();

    @Field("enabled")
    private Boolean enabled = Boolean.TRUE;

    @Field("last_login_at")
    private OffsetDateTime lastLoginAt;

    @Field("created_at")
    private OffsetDateTime createdAt;

    @Field("updated_at")
    private OffsetDateTime updatedAt;

    public String getIdAsString() {
        return id == null ? null : id.toHexString();
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public boolean hasRole(UserRole role) {
        return roles != null && roles.contains(role);
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(OffsetDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
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
