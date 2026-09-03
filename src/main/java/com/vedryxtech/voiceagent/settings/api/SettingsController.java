package com.vedryxtech.voiceagent.settings.api;

import com.vedryxtech.voiceagent.settings.api.dto.CallPolicyPatchRequest;
import com.vedryxtech.voiceagent.settings.api.dto.SettingsResponse;
import com.vedryxtech.voiceagent.settings.application.CallPolicyMerger;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The installation-wide settings, exposed under the legacy {@code /api/v1/organizations/current}
 * path for compatibility with the highrise voice agent.
 *
 * <p>The path predates the single-tenant rework; the agent reads {@code payload.callPolicy}
 * on start-up (see {@code highrise/agent/backend.py:105-113}). Renaming or nesting the
 * {@code callPolicy} key would break that read, so this controller preserves it verbatim
 * while the underlying data now lives on the {@link AppSettings} singleton.</p>
 */
@Tag(name = "2. Settings",
        description = "Installation-wide settings: the calling policy the agent reads and the "
                + "admin surface for changing it.")
@RestController
@RequestMapping(path = "/api/v1/organizations", produces = "application/json")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Frozen shape: {@code callPolicy} key at the top level, exact CallPolicy field names.
     * Readable by ADMIN, MEMBER and API_CLIENT (highrise authenticates with X-API-Key and
     * must not be locked out of its own policy read).
     */
    @Operation(summary = "Installation settings and the calling rules the agent reads")
    @GetMapping("/current")
    @PreAuthorize("hasAnyRole('ADMIN', 'MEMBER', 'API_CLIENT')")
    public SettingsResponse current() {
        AppSettings settings = settingsService.current();
        CallPolicy policy = settings.getCallPolicy() != null
                ? settings.getCallPolicy()
                : CallPolicy.defaults();
        return new SettingsResponse(
                policy,
                settings.getTimezone(),
                settings.getApiKeyPrefix(),
                settings.getApiKeyCreatedAt());
    }

    /**
     * Partial update. Omitted fields are left as they are — the pre-rework behaviour of
     * silently resetting them to class defaults is gone (fixes QA H-1).
     */
    @Operation(summary = "Change the calling rules",
            description = "Send only the fields you want to change. Anything omitted is kept "
                    + "as it is. Admins only. 422 with fieldErrors on validation failure.")
    @PutMapping(path = "/current/call-policy", consumes = "application/json")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsResponse updateCallPolicy(@RequestBody CallPolicyPatchRequest patch) {
        CallPolicy stored = settingsService.currentPolicy();
        CallPolicy merged = CallPolicyMerger.merge(stored, patch);
        AppSettings saved = settingsService.updateCallPolicy(merged);
        return new SettingsResponse(
                saved.getCallPolicy(),
                saved.getTimezone(),
                saved.getApiKeyPrefix(),
                saved.getApiKeyCreatedAt());
    }
}
