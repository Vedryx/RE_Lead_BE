package com.vedryxtech.voiceagent.auth.api;

import com.vedryxtech.voiceagent.auth.api.dto.LoginRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import com.vedryxtech.voiceagent.auth.application.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication and current-session inspection.
 *
 * <p>{@code /login} is the only endpoint under this controller that does not require a token.</p>
 */
@Tag(name = "1. Auth",
        description = "Get a token, then inspect the current signed-in user.")
@RestController
@RequestMapping(path = "/api/v1/auth", produces = "application/json")
public class AuthController {

    private final AuthService authService;
    
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "Log in and get a token",
            description = "Returns an accessToken. Copy it, click the Authorize button at the top of this page, "
                    + "and paste it in. Everything else on this page will then work.")
    @SecurityRequirements
    @PostMapping(path = "/login", consumes = "application/json")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @Operation(summary = "Who am I",
            description = "Shows the account behind the token you are using. Handy to check the token still works.")
    @GetMapping("/me")
    public UserResponse me() {
        return authService.currentUser();
    }
}
