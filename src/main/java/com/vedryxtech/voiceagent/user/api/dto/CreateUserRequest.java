package com.vedryxtech.voiceagent.user.api.dto;

import com.vedryxtech.voiceagent.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** An admin adding a teammate. Single-tenant: no organization to pick. */
public record CreateUserRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Schema(example = "member@vedryxtech.com")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 100, message = "password must be at least 8 characters")
        @Schema(example = "Member@12345")
        String password,

        @NotBlank(message = "fullName is required")
        @Size(max = 150)
        @Schema(example = "Floor Member")
        String fullName,

        @Size(max = 25)
        String phone,

        @NotEmpty(message = "at least one role is required")
        @Schema(example = "[\"member\"]", description = "admin or member")
        Set<UserRole> roles
) {
}
