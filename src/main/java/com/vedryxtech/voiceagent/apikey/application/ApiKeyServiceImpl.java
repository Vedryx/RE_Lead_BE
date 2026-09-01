package com.vedryxtech.voiceagent.apikey.application;

import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.organization.persistence.OrganizationRepository;
import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.organization.application.OrganizationService;
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
import java.util.Optional;

@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyServiceImpl.class);

    /** Recognisable at a glance in logs and config files. */
    private static final String PREFIX = "vdx_";
    private static final int RANDOM_BYTES = 32;
    private static final int VISIBLE_PREFIX_LENGTH = 12;

    private final OrganizationRepository repository;
    private final OrganizationService organizationService;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyServiceImpl(OrganizationRepository repository, OrganizationService organizationService) {
        this.repository = repository;
        this.organizationService = organizationService;
    }

    @Override
    public GeneratedKey generate() {
        Organization organization = organizationService.current();
        String plainKey = newKey();
        apply(organization, plainKey);
        log.info("Issued a new API key for organization {}", organization.getSlug());
        return new GeneratedKey(plainKey, organization.getApiKeyPrefix());
    }

    @Override
    public void seed(Organization organization, String plainKey) {
        apply(organization, plainKey);
        log.warn("Seeded the development API key for organization {} - rotate it before going live",
                organization.getSlug());
    }

    @Override
    public Optional<Organization> resolve(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return Optional.empty();
        }
        return repository.findByApiKeyHash(sha256(plainKey.trim()));
    }

    @Override
    public void revoke() {
        Organization organization = organizationService.current();
        organization.setApiKeyHash(null);
        organization.setApiKeyPrefix(null);
        organization.setApiKeyCreatedAt(null);
        organization.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        repository.save(organization);
        log.warn("Revoked the API key for organization {}", organization.getSlug());
    }

    private void apply(Organization organization, String plainKey) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        organization.setApiKeyHash(sha256(plainKey));
        organization.setApiKeyPrefix(plainKey.substring(0, Math.min(VISIBLE_PREFIX_LENGTH, plainKey.length())));
        organization.setApiKeyCreatedAt(now);
        organization.setUpdatedAt(now);
        repository.save(organization);
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
