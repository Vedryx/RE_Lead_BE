package com.vedryxtech.voiceagent.organization.api;

import com.vedryxtech.voiceagent.organization.domain.CallPolicy;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.organization.api.dto.OrganizationResponse;
import com.vedryxtech.voiceagent.organization.api.dto.RegisterOrganizationRequest;
import com.vedryxtech.voiceagent.user.mapper.AccountMapper;
import com.vedryxtech.voiceagent.auth.application.AuthService;
import com.vedryxtech.voiceagent.organization.application.OrganizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. Organizations",
        description = "Create the organization, inspect the current installation, and manage its call policy.")
@RestController
@RequestMapping(path = "/api/v1/organizations", produces = "application/json")
public class OrganizationController {

    private final AuthService authService;
    private final OrganizationService organizationService;
    private final AccountMapper mapper;

    public OrganizationController(AuthService authService,
                                  OrganizationService organizationService,
                                  AccountMapper mapper) {
        this.authService = authService;
        this.organizationService = organizationService;
        this.mapper = mapper;
    }

    @Operation(summary = "Sign up a new company",
            description = "Creates the organization and its first admin, and returns a token you can use right away. "
                    + "No token needed to call this.")
    @SecurityRequirements
    @PostMapping(consumes = "application/json")
    public ResponseEntity<LoginResponse> create(@Valid @RequestBody RegisterOrganizationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registerOrganization(request));
    }

    @Operation(summary = "Current organization and its calling rules")
    @GetMapping("/current")
    public OrganizationResponse current() {
        return mapper.toResponse(organizationService.current());
    }

    @Operation(summary = "Change the calling rules",
            description = "How many times to retry, how long to wait after each kind of failed call, "
                    + "and the hours of day calls are allowed. Admins only.")
    @PutMapping(path = "/current/call-policy", consumes = "application/json")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public OrganizationResponse updateCallPolicy(@RequestBody CallPolicy policy) {
        return mapper.toResponse(organizationService.updateCallPolicy(policy));
    }
}
