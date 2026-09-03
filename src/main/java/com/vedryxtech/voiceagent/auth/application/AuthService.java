package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.auth.api.dto.LoginRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    /** Rotates the refresh token, re-checks the user is enabled (H-3), returns a fresh pair. */
    LoginResponse refresh(String presentedRefreshToken);

    /** Revokes the presented refresh token. Idempotent. */
    void logout(String presentedRefreshToken);

    /** The caller behind the current access token. */
    UserResponse currentUser();
}
