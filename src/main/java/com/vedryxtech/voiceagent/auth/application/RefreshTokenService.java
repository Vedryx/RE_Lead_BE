package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.user.domain.User;

/**
 * Owns the refresh_token collection: issuance on login, one-time-use rotation on refresh,
 * revocation on logout.
 *
 * <p>The plaintext refresh token is returned exactly twice — from {@link #issue(User)} and
 * from {@link #rotate(String)} — and never persisted. The stored document holds a SHA-256
 * hash so a database dump does not hand out live sessions.</p>
 */
public interface RefreshTokenService {

    /** Mints and stores a fresh 30-day refresh token for the given user. */
    Issued issue(User user);

    /**
     * Validates the presented refresh token, revokes it, mints a successor, and returns both
     * the new refresh token and the user it belongs to (so the caller can also issue a new
     * access token). Throws {@link com.vedryxtech.voiceagent.exception.UnauthorizedException}
     * if the token is unknown, expired, revoked or already rotated (replay).
     */
    Rotated rotate(String presented);

    /** Revokes the presented token if it exists. Idempotent: an unknown token is a no-op. */
    void revoke(String presented);

    record Issued(String token, long expiresInSeconds) {
    }

    record Rotated(User user, String newRefreshToken, long expiresInSeconds) {
    }
}
