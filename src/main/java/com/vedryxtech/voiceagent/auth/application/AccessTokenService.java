package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.user.domain.User;

public interface AccessTokenService {

    /** Mints the signed access token that carries the user, the tenant and the roles. */
    IssuedToken issue(User user, Organization organization);

    record IssuedToken(String token, long expiresInSeconds) {
    }
}
