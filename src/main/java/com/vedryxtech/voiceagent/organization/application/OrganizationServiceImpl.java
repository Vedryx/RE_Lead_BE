package com.vedryxtech.voiceagent.organization.application;

import com.vedryxtech.voiceagent.organization.domain.CallPolicy;
import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.organization.domain.OrganizationStatus;
import com.vedryxtech.voiceagent.organization.api.dto.RegisterOrganizationRequest;
import com.vedryxtech.voiceagent.exception.DuplicateResourceException;
import com.vedryxtech.voiceagent.exception.ResourceNotFoundException;
import com.vedryxtech.voiceagent.organization.persistence.OrganizationRepository;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

@Service
public class OrganizationServiceImpl implements OrganizationService {

    private static final Logger log = LoggerFactory.getLogger(OrganizationServiceImpl.class);
    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    private final OrganizationRepository repository;

    public OrganizationServiceImpl(OrganizationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Organization create(RegisterOrganizationRequest request) {
        String slug = slugify(request.slug() != null ? request.slug() : request.organizationName());
        if (repository.existsBySlug(slug)) {
            throw new DuplicateResourceException("An organization with slug '" + slug + "' already exists");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Organization organization = new Organization();
        organization.setName(request.organizationName().trim());
        organization.setSlug(slug);
        organization.setStatus(OrganizationStatus.ACTIVE);
        organization.setContactEmail(request.adminEmail().trim().toLowerCase(Locale.ROOT));
        organization.setContactPhone(request.contactPhone());
        organization.setTimezone(request.timezone() != null && !request.timezone().isBlank()
                ? request.timezone().trim()
                : DEFAULT_TIMEZONE);
        organization.setCallPolicy(CallPolicy.defaults());
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);

        Organization saved = repository.save(organization);
        log.info("Created organization '{}' ({})", saved.getName(), saved.getIdAsString());
        return saved;
    }

    /**
     * There is one organization per installation. If more than one row exists - because someone
     * called the signup endpoint twice - the oldest is the real one.
     */
    @Override
    public Organization current() {
        List<Organization> all = repository.findAll(Sort.by(Sort.Direction.ASC, "created_at"));
        if (all.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No organization exists yet. Start the application with bootstrap enabled, "
                            + "or call POST /api/v1/organizations.");
        }
        return all.get(0);
    }

    @Override
    public Organization require(String organizationId) {
        if (organizationId == null || !ObjectId.isValid(organizationId)) {
            throw ResourceNotFoundException.of("organization", "id", String.valueOf(organizationId));
        }
        return repository.findById(new ObjectId(organizationId))
                .orElseThrow(() -> ResourceNotFoundException.of("organization", "id", organizationId));
    }

    @Override
    public Organization updateCallPolicy(CallPolicy policy) {
        Organization organization = current();
        organization.setCallPolicy(policy != null ? policy : CallPolicy.defaults());
        organization.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return repository.save(organization);
    }

    @Override
    public CallPolicy currentPolicy() {
        CallPolicy policy = current().getCallPolicy();
        return policy != null ? policy : CallPolicy.defaults();
    }

    /** {@code "My Home Sanctuary"} becomes {@code "my-home-sanctuary"}. */
    private static String slugify(String raw) {
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.isEmpty()) {
            throw new IllegalArgumentException("Could not derive a slug from '" + raw + "'");
        }
        return slug.length() > 63 ? slug.substring(0, 63) : slug;
    }
}
