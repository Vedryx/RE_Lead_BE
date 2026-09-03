package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.auth.domain.RefreshToken;
import com.vedryxtech.voiceagent.auth.persistence.RefreshTokenRepository;
import com.vedryxtech.voiceagent.exception.UnauthorizedException;
import com.vedryxtech.voiceagent.user.application.UserService;
import com.vedryxtech.voiceagent.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenServiceImpl.class);

    /** Recognisable at a glance in logs; separate from the vdx_ API-key namespace. */
    private static final String PREFIX = "vrt_";
    private static final int RANDOM_BYTES = 48;
    private static final Duration TTL = Duration.ofDays(30);
    private static final String INVALID_REFRESH = "Refresh token is invalid or expired";

    private final RefreshTokenRepository repository;
    private final UserService userService;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenServiceImpl(RefreshTokenRepository repository, UserService userService) {
        this.repository = repository;
        this.userService = userService;
    }

    @Override
    public Issued issue(User user) {
        String plain = newToken();
        persist(plain, user);
        return new Issued(plain, TTL.toSeconds());
    }

    @Override
    public Rotated rotate(String presented) {
        if (presented == null || presented.isBlank()) {
            throw new UnauthorizedException(INVALID_REFRESH);
        }
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String hash = sha256(presented.trim());

        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new UnauthorizedException(INVALID_REFRESH));

        if (existing.isRevoked() || existing.isExpired(now) || existing.isRotated()) {
            // A replayed refresh token is a red flag; note it but do not leak which shoe dropped.
            log.warn("Refused refresh token id={} (revoked={} expired={} rotated={})",
                    existing.getIdAsString(), existing.isRevoked(),
                    existing.isExpired(now), existing.isRotated());
            throw new UnauthorizedException(INVALID_REFRESH);
        }

        // Re-check the user exists AND is enabled. Closes H-3: a disabled or deleted user's
        // refresh must not mint a new access token.
        User user = userService.findById(existing.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(() -> {
                    // Also poison this token so a second attempt short-circuits at the repo lookup.
                    existing.setRevokedAt(now);
                    repository.save(existing);
                    return new UnauthorizedException(INVALID_REFRESH);
                });

        // Mint the successor first, then mark the old one rotated. If persistence fails partway
        // through, the successor is at worst orphaned; the old token stays valid rather than
        // us handing out a new one whose predecessor we cannot invalidate.
        String successorPlain = newToken();
        persist(successorPlain, user);

        existing.setRotatedTo(sha256(successorPlain));
        existing.setRevokedAt(now);
        repository.save(existing);

        return new Rotated(user, successorPlain, TTL.toSeconds());
    }

    @Override
    public void revoke(String presented) {
        if (presented == null || presented.isBlank()) {
            return;
        }
        Optional<RefreshToken> found = repository.findByTokenHash(sha256(presented.trim()));
        if (found.isEmpty()) {
            return;
        }
        RefreshToken token = found.get();
        if (token.isRevoked()) {
            return;
        }
        token.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(token);
    }

    private void persist(String plain, User user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        RefreshToken token = new RefreshToken();
        token.setTokenHash(sha256(plain));
        token.setUserId(user.getIdAsString());
        token.setCreatedAt(now);
        token.setExpiresAt(now.plus(TTL));
        repository.save(token);
    }

    private String newToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        random.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
