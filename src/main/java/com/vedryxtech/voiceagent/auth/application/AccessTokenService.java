package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.user.domain.User;

/**
 * Mints the short-lived (15 min) HS256 access token. Single-tenant: the token carries the
 * subject, email, name and roles — no {@code org_slug} claim any more.
 */
public interface AccessTokenService {

    IssuedToken issue(User user);

    record IssuedToken(String token, long expiresInSeconds) {
    }
}
