package com.vedryxtech.voiceagent.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.config.SecurityProperties;
import com.vedryxtech.voiceagent.exception.DuplicateResourceException;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import com.vedryxtech.voiceagent.lead.application.LeadService;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;
import com.vedryxtech.voiceagent.user.application.UserService;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Set;

/**
 * First-boot setup, in one place so the ordering is explicit:
 *
 * <ol>
 *   <li>ensure the {@code app_settings} singleton exists, with defaults, and picks up the
 *       configured timezone if the doc is being created;</li>
 *   <li>create the single admin user if the database has none;</li>
 *   <li>install the API key from configuration when no key is stored yet;</li>
 *   <li>optionally load {@code seed/leads.json}.</li>
 * </ol>
 *
 * <p>Idempotent: restarting never duplicates or overwrites anything that already exists.</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String SEED_FILE = "seed/leads.json";

    private final SecurityProperties securityProperties;
    private final SettingsService settingsService;
    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final LeadService leadService;
    private final ObjectMapper objectMapper;

    public DataInitializer(SecurityProperties securityProperties,
                           SettingsService settingsService,
                           UserService userService,
                           ApiKeyService apiKeyService,
                           LeadService leadService,
                           ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.settingsService = settingsService;
        this.userService = userService;
        this.apiKeyService = apiKeyService;
        this.leadService = leadService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.Bootstrap bootstrap = securityProperties.getBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }

        AppSettings settings = ensureSettings(bootstrap);
        ensureAdmin(bootstrap);
        installApiKey(settings, bootstrap.getApiKey());

        if (bootstrap.isSeedLeads()) {
            seedLeads();
        }
    }

    /**
     * Reads the singleton so {@link SettingsService#current()} creates it lazily on first
     * boot, then updates the timezone iff we just created a doc that has none in config.
     */
    private AppSettings ensureSettings(SecurityProperties.Bootstrap bootstrap) {
        AppSettings settings = settingsService.current();
        String configuredTz = bootstrap.getTimezone();
        if (configuredTz != null && !configuredTz.isBlank()
                && (settings.getTimezone() == null || settings.getTimezone().isBlank())) {
            settings.setTimezone(configuredTz.trim());
            settings = settingsService.save(settings);
            log.info("Set installation timezone to {}", settings.getTimezone());
        }
        return settings;
    }

    private void ensureAdmin(SecurityProperties.Bootstrap bootstrap) {
        String email = bootstrap.getAdminEmail();
        if (email == null || email.isBlank()) {
            log.warn("Bootstrap enabled but no admin-email configured; skipping admin creation");
            return;
        }
        if (userService.findByEmail(email).isPresent()) {
            return;
        }
        if (bootstrap.getAdminPassword() == null || bootstrap.getAdminPassword().isBlank()) {
            log.warn("Bootstrap admin-email is set but admin-password is missing; cannot create admin");
            return;
        }
        try {
            User admin = userService.create(new CreateUserRequest(
                    email,
                    bootstrap.getAdminPassword(),
                    bootstrap.getAdminName() == null ? "Administrator" : bootstrap.getAdminName(),
                    null,
                    Set.of(UserRole.ADMIN)));
            log.info("Created bootstrap admin {}", admin.getEmail());
        } catch (DuplicateResourceException ex) {
            log.debug("Admin {} already exists; nothing to do", email);
        }
    }

    /**
     * Installs the configured key only when no key is stored, so a key rotated through the
     * API is never silently reset back to the one in the config file.
     */
    private void installApiKey(AppSettings settings, String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return;
        }
        if (settings.getApiKeyHash() != null) {
            return;
        }
        apiKeyService.seed(configuredKey.trim());
    }

    /**
     * Loads the sample leads. Rows whose phone number is already taken are reported and
     * skipped, because one lead is kept per phone number.
     */
    private void seedLeads() {
        ClassPathResource resource = new ClassPathResource(SEED_FILE);
        if (!resource.exists()) {
            log.warn("Seeding enabled but {} was not found on the classpath", SEED_FILE);
            return;
        }

        List<LeadRequest> requests;
        try (InputStream in = resource.getInputStream()) {
            CollectionType listType = objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, LeadRequest.class);
            requests = objectMapper.readValue(in, listType);
        } catch (Exception ex) {
            log.error("Could not read {}: {}", SEED_FILE, ex.getMessage());
            return;
        }

        int inserted = 0;
        int skipped = 0;
        for (LeadRequest request : requests) {
            try {
                leadService.create(request);
                inserted++;
            } catch (DuplicateResourceException ex) {
                skipped++;
                log.debug("Seed row for '{}' ({}) already exists", request.name(), request.phone());
            } catch (RuntimeException ex) {
                skipped++;
                log.warn("Skipped invalid seed row for '{}': {}", request.name(), ex.getMessage());
            }
        }
        if (inserted > 0 || skipped > 0) {
            log.info("Sample leads: {} inserted, {} already present, out of {} rows",
                    inserted, skipped, requests.size());
        }
    }
}
