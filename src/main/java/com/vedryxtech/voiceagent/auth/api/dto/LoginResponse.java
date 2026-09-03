package com.vedryxtech.voiceagent.auth.api.dto;

import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Returned by {@code POST /api/v1/auth/login} and {@code POST /api/v1/auth/refresh}.
 *
 * <p>The pre-rework {@code organization} field was dropped; the highrise voice agent
 * authenticates by X-API-Key and never touches this shape, so this is a dashboard-only
 * change.</p>
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        @Schema(description = "Opaque, one-time-use, 30-day refresh token. Store it and swap "
                + "at POST /auth/refresh to get a new access token.")
        String refreshToken,
        long refreshExpiresInSeconds,
        UserResponse user
) {

    public static LoginResponse bearer(String token, long expiresInSeconds,
                                       String refreshToken, long refreshExpiresInSeconds,
                                       UserResponse user) {
        return new LoginResponse(token, "Bearer", expiresInSeconds,
                refreshToken, refreshExpiresInSeconds, user);
    }
}
