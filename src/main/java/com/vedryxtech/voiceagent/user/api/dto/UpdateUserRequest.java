package com.vedryxtech.voiceagent.user.api.dto;

import jakarta.validation.constraints.NotNull;

/** Partial user update operations currently supported by the API. */
public record UpdateUserRequest(

        @NotNull(message = "enabled is required")
        Boolean enabled
) {
}
