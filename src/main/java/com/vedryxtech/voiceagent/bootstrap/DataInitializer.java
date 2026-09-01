package com.vedryxtech.voiceagent.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.vedryxtech.voiceagent.config.SecurityProperties;
import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import com.vedryxtech.voiceagent.organization.api.dto.RegisterOrganizationRequest;
import com.vedryxtech.voiceagent.exception.DuplicateResourceException;
import com.vedryxtech.voiceagent.organization.persistence.OrganizationRepository;
import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.lead.application.LeadService;
import com.vedryxtech.voiceagent.organization.application.OrganizationService;
import com.vedryxtech.voiceagent.user.application.UserService;
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
 *   <li>create the organization and its first admin when the database is empty;</li>
 *   <li>install the API key from configuration, so the AI agent can connect straight away;</li>
 *   <li>optionally load {@code seed/leads.json}.</li>
 * </ol>
 *
 * <p>Everything here is idempotent: restarting the application does not duplicate or overwrite
 * anything that already exists.</p>
 */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String SEED_FILE = "seed/leads.json";

    private final SecurityProperties securityProperties;
    private final OrganizationRepository organizationRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final ApiKeyService apiKeyService;
    private final LeadService leadService;
    private final ObjectMapper objectMapper;

    public DataInitializer(SecurityProperties securityProperties,
                           OrganizationRepository organizationRepository,
                           OrganizationService organizationService,
                           UserService userService,
                           ApiKeyService apiKeyService,
                           LeadService leadService,
                           ObjectMapper objectMapper) {
        this.securityProperties = securityProperties;
        this.organizationRepository = organizationRepository;
        this.organizationService = organizationService;
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

        Organization organization = organizationRepository.findBySlug(bootstrap.getOrganizationSlug())
                .orElseGet(() -> createOrganization(bootstrap));

        installApiKey(organization, bootstrap.getApiKey());

        if (bootstrap.isSeedLeads()) {
            seedLeads();
        }
    }

    private Organization createOrganization(SecurityProperties.Bootstrap bootstrap) {
        RegisterOrganizationRequest request = new RegisterOrganizationRequest(
                bootstrap.getOrganizationName(),
                bootstrap.getOrganizationSlug(),
                bootstrap.getTimezone(),
                bootstrap.getAdminEmail(),
                bootstrap.getAdminPassword(),
                bootstrap.getAdminName(),
                null);

        Organization organization = organizationService.create(request);

        if (userService.findByEmail(bootstrap.getAdminEmail()).isEmpty()) {
            User admin = userService.create(organization.getIdAsString(), new CreateUserRequest(
                    bootstrap.getAdminEmail(),
                    bootstrap.getAdminPassword(),
                    bootstrap.getAdminName(),
                    null,
                    Set.of(UserRole.ORG_ADMIN)));
            log.info("Created organization '{}' with admin {}", organization.getName(), admin.getEmail());
        }
        return organization;
    }

    /**
     * Installs the configured key only when the organization has none, so a key rotated
     * through the API is never silently reset back to the one in the config file.
     */
    private void installApiKey(Organization organization, String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            return;
        }
        if (organization.getApiKeyHash() != null) {
            return;
        }
        apiKeyService.seed(organization, configuredKey.trim());
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
