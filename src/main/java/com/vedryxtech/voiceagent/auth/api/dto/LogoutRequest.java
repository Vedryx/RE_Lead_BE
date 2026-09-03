package com.vedryxtech.voiceagent.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** The refresh token to revoke. */
public record LogoutRequest(
        @NotBlank(message = "refreshToken is required")
        @Schema(example = "vrt_1a2b3c...")
        String refreshToken
) {
}
