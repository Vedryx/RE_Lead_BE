package com.vedryxtech.voiceagent.auth.api.dto;

import com.vedryxtech.voiceagent.organization.api.dto.OrganizationResponse;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;

/** Returned by {@code POST /api/v1/auth/login}. The token carries the organization scope. */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        UserResponse user,
        OrganizationResponse organization
) {

    public static LoginResponse bearer(String token, long expiresInSeconds,
                                       UserResponse user, OrganizationResponse organization) {
        return new LoginResponse(token, "Bearer", expiresInSeconds, user, organization);
    }
}
