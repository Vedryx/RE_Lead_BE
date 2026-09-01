package com.vedryxtech.voiceagent.organization.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-serve signup: creates the organization and its first ORG_ADMIN in one call. */
public record RegisterOrganizationRequest(

        @NotBlank(message = "organizationName is required")
        @Size(max = 200)
        @Schema(example = "Acme Realty")
        String organizationName,

        @Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,62}$",
                message = "slug must be lowercase letters, digits and dashes")
        @Schema(example = "acme-realty", description = "Optional. Derived from the company name if left out.")
        String slug,

        @Size(max = 64)
        @Schema(example = "Asia/Kolkata", description = "Used for calling hours and daily charts")
        String timezone,

        @NotBlank(message = "adminEmail is required")
        @Email(message = "adminEmail must be a valid address")
        @Schema(example = "admin@acme.test")
        String adminEmail,

        @NotBlank(message = "adminPassword is required")
        @Size(min = 8, max = 100, message = "adminPassword must be at least 8 characters")
        @Schema(example = "Acme@12345")
        String adminPassword,

        @NotBlank(message = "adminFullName is required")
        @Size(max = 150)
        @Schema(example = "Acme Admin")
        String adminFullName,

        @Size(max = 25)
        String contactPhone
) {
}
