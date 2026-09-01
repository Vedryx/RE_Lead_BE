package com.vedryxtech.voiceagent.user.api.dto;

import com.vedryxtech.voiceagent.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** An org admin adding a teammate. The new user inherits the caller's organization. */
public record CreateUserRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Schema(example = "agent@vedryxtech.com")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be at least 8 characters")
        @Schema(example = "Agent@12345")
        String password,

        @NotBlank(message = "fullName is required")
        @Size(max = 150)
        @Schema(example = "Floor Agent")
        String fullName,

        @Size(max = 25)
        String phone,

        @NotEmpty(message = "at least one role is required")
        @Schema(example = "[\"agent\"]", description = "org_admin, manager, agent or viewer")
        Set<UserRole> roles
) {
}
