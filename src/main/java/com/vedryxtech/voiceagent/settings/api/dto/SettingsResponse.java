package com.vedryxtech.voiceagent.settings.api.dto;

import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * The public shape of {@code GET /api/v1/organizations/current}. The path is preserved for
 * agent compatibility (highrise reads {@code callPolicy} at start-up); the payload is now the
 * installation-wide settings rather than a tenant document.
 *
 * <p>The {@code callPolicy} key must not be renamed or nested — highrise reads it directly.</p>
 */
@Schema(description = "Installation-wide settings. The callPolicy key is the frozen contract "
        + "with the voice agent; the api-key metadata is dashboard convenience.")
public record SettingsResponse(
        CallPolicy callPolicy,
        String timezone,
        String apiKeyPrefix,
        OffsetDateTime apiKeyCreatedAt
) {
}
