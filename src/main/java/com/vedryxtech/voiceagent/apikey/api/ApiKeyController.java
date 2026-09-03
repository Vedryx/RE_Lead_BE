package com.vedryxtech.voiceagent.apikey.api;

import com.vedryxtech.voiceagent.apikey.api.dto.ApiKeyResponse;
import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
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
        description = "The AI agent API key, kept out of the login / user management surface.")
@RestController
@RequestMapping(path = "/api/v1/api-keys", produces = "application/json")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final SettingsService settingsService;

    public ApiKeyController(ApiKeyService apiKeyService, SettingsService settingsService) {
        this.apiKeyService = apiKeyService;
        this.settingsService = settingsService;
    }

    @Operation(summary = "Create or rotate the API key",
            description = "The full key is returned once and never again. Admins only.")
    @PostMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiKeyResponse> create() {
        ApiKeyService.GeneratedKey generated = apiKeyService.generate();
        AppSettings settings = settingsService.current();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiKeyResponse.created(
                generated.apiKey(),
                generated.prefix(),
                settings.getApiKeyCreatedAt()));
    }

    @Operation(summary = "Show the current API key prefix",
            description = "Only the prefix and creation time — the key itself is stored hashed.")
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER')")
    public ApiKeyResponse current() {
        AppSettings settings = settingsService.current();
        return ApiKeyResponse.existing(settings.getApiKeyPrefix(), settings.getApiKeyCreatedAt());
    }

    @Operation(summary = "Revoke the API key",
            description = "The agent stops being able to reach this application immediately.")
    @DeleteMapping("/current")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revoke() {
        apiKeyService.revoke();
        return ResponseEntity.noContent().build();
    }
}
