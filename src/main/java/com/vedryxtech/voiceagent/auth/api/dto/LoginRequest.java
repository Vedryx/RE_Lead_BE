package com.vedryxtech.voiceagent.auth.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Schema(example = "admin@vedryxtech.com", description = "The bootstrap admin created on first start")
        String email,

        @NotBlank(message = "password is required")
        @Schema(example = "Admin@12345")
        String password
) {
}
