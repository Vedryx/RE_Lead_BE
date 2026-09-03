package com.vedryxtech.voiceagent.apikey.application;

import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyServiceImpl.class);

    /** Recognisable at a glance in logs and config files. */
    private static final String PREFIX = "vdx_";
    private static final int RANDOM_BYTES = 32;
    private static final int VISIBLE_PREFIX_LENGTH = 12;

    private final SettingsService settingsService;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyServiceImpl(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public GeneratedKey generate() {
        AppSettings settings = settingsService.current();
        String plainKey = newKey();
        apply(settings, plainKey);
        log.info("Issued a new API key");
        return new GeneratedKey(plainKey, settings.getApiKeyPrefix());
    }

    @Override
    public void seed(String plainKey) {
        AppSettings settings = settingsService.current();
        apply(settings, plainKey);
        log.warn("Seeded the development API key - rotate it before going live");
    }

    @Override
    public boolean matches(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return false;
        }
        String stored = settingsService.current().getApiKeyHash();
        if (stored == null || stored.isBlank()) {
            return false;
        }
        // Constant-time comparison: neither branch of the equality check should leak
        // whether a real key was configured at all.
        return MessageDigest.isEqual(
                stored.getBytes(StandardCharsets.US_ASCII),
                sha256(plainKey.trim()).getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public void revoke() {
        AppSettings settings = settingsService.current();
        settings.setApiKeyHash(null);
        settings.setApiKeyPrefix(null);
        settings.setApiKeyCreatedAt(null);
        settingsService.save(settings);
        log.warn("Revoked the API key");
    }

    private void apply(AppSettings settings, String plainKey) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        settings.setApiKeyHash(sha256(plainKey));
        settings.setApiKeyPrefix(plainKey.substring(0, Math.min(VISIBLE_PREFIX_LENGTH, plainKey.length())));
        settings.setApiKeyCreatedAt(now);
        settingsService.save(settings);
    }

    private String newKey() {
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
