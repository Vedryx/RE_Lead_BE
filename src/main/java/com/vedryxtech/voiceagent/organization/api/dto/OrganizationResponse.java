package com.vedryxtech.voiceagent.organization.api.dto;

import com.vedryxtech.voiceagent.organization.domain.CallPolicy;
import com.vedryxtech.voiceagent.organization.domain.OrganizationStatus;

import java.time.OffsetDateTime;

public record OrganizationResponse(
        String id,
        String name,
        String slug,
        OrganizationStatus status,
        String contactEmail,
        String contactPhone,
        String timezone,
        CallPolicy callPolicy,
        String apiKeyPrefix,
        OffsetDateTime apiKeyCreatedAt,
        OffsetDateTime createdAt
) {
}
