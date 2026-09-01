package com.vedryxtech.voiceagent.organization.application;

import com.vedryxtech.voiceagent.organization.domain.CallPolicy;
import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.organization.api.dto.RegisterOrganizationRequest;

public interface OrganizationService {

    Organization create(RegisterOrganizationRequest request);

    /** The organization this installation belongs to. */
    Organization current();

    Organization require(String organizationId);

    Organization updateCallPolicy(CallPolicy policy);

    /** The calling rules in force, falling back to the built-in defaults. */
    CallPolicy currentPolicy();
}
