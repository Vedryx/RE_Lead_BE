package com.vedryxtech.voiceagent.user.api.dto;

import com.vedryxtech.voiceagent.user.domain.UserRole;

import java.time.OffsetDateTime;
import java.util.Set;

/**
 * A user as returned by the API. The password hash is never part of this.
 *
 * <p>The pre-rework field {@code organizationId} was removed — single-tenant now.</p>
 */
public record UserResponse(
        String id,
        String email,
        String fullName,
        String phone,
        Set<UserRole> roles,
        Boolean enabled,
        OffsetDateTime lastLoginAt,
        OffsetDateTime createdAt
) {
}
