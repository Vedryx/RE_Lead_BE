package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.auth.api.dto.LoginRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.organization.api.dto.RegisterOrganizationRequest;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;

public interface AuthService {

    /** Creates the organization and its first admin, then returns a usable token. */
    LoginResponse registerOrganization(RegisterOrganizationRequest request);

    LoginResponse login(LoginRequest request);

    /** The caller behind the current access token. */
    UserResponse currentUser();
}
