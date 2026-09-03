package com.vedryxtech.voiceagent.auth.api;

import com.vedryxtech.voiceagent.auth.api.dto.LoginRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.auth.api.dto.LogoutRequest;
import com.vedryxtech.voiceagent.auth.api.dto.RefreshRequest;
import com.vedryxtech.voiceagent.auth.application.AuthService;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, refresh, logout, whoami.
 *
 * <p>Login and refresh are public. Logout is public (posting a revoked or unknown token
 * is a no-op — the point is to invalidate whatever the client holds). Whoami requires a
 * valid access token.</p>
 */
@Tag(name = "1. Auth", description = "Log in, refresh access, log out.")
@RestController
@RequestMapping(path = "/api/v1/auth", produces = "application/json")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Log in", description = "Returns a 15-minute access token and a 30-day refresh token.")
    @SecurityRequirements
    @PostMapping(path = "/login", consumes = "application/json")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Swap a refresh token for a new access token",
            description = "Rotates the refresh token — the old one is invalidated immediately. "
                    + "A disabled or deleted user's refresh is rejected (H-3).")
    @SecurityRequirements
    @PostMapping(path = "/refresh", consumes = "application/json")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Log out",
            description = "Revokes the presented refresh token. Idempotent: an unknown or "
                    + "already-revoked token is a 204.")
    @SecurityRequirements
    @PostMapping(path = "/logout", consumes = "application/json")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Who am I")
    @GetMapping("/me")
    public UserResponse me() {
        return authService.currentUser();
    }
}
