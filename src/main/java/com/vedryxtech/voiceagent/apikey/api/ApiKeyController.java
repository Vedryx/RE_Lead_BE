package com.vedryxtech.voiceagent.apikey.api;

import com.vedryxtech.voiceagent.apikey.api.dto.ApiKeyResponse;
import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.organization.application.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3. API Keys",
        description = "Manage the AI agent API key separately from login and user administration.")
@RestController
@RequestMapping(path = "/api/v1/api-keys", produces = "application/json")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final OrganizationService organizationService;

    public ApiKeyController(ApiKeyService apiKeyService, OrganizationService organizationService) {
        this.apiKeyService = apiKeyService;
        this.organizationService = organizationService;
    }

    @Operation(summary = "Create the current organization API key",
            description = "Generates a key for the voice-agent application to authenticate with. "
                    + "The full key is returned once and never again. "
                    + "Calling this a second time replaces the old key immediately.")
    @PostMapping("/current")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiKeyResponse> create() {
        ApiKeyService.GeneratedKey generated = apiKeyService.generate();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiKeyResponse.created(
                generated.apiKey(),
                generated.prefix(),
                organizationService.current().getApiKeyCreatedAt()));
    }

    @Operation(summary = "Show the current organization API key prefix",
            description = "Returns only the prefix and when it was created. The key itself is stored "
                    + "hashed and cannot be read back; if it is lost, create a new one.")
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'MANAGER', 'SUPER_ADMIN')")
    public ApiKeyResponse current() {
        var organization = organizationService.current();
        return ApiKeyResponse.existing(organization.getApiKeyPrefix(), organization.getApiKeyCreatedAt());
    }

    @Operation(summary = "Revoke the current organization API key",
            description = "The AI agent stops being able to reach this application immediately.")
    @DeleteMapping("/current")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> revoke() {
        apiKeyService.revoke();
        return ResponseEntity.noContent().build();
    }
}
