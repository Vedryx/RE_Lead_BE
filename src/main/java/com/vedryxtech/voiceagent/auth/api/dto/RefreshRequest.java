package com.vedryxtech.voiceagent.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** The refresh token issued by login or a prior refresh. */
public record RefreshRequest(
        @NotBlank(message = "refreshToken is required")
        @Schema(example = "vrt_1a2b3c...")
        String refreshToken
) {
}
