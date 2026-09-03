package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.auth.api.dto.LoginRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.exception.TooManyRequestsException;
import com.vedryxtech.voiceagent.exception.UnauthorizedException;
import com.vedryxtech.voiceagent.security.CurrentActor;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import com.vedryxtech.voiceagent.user.application.UserService;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Same message for unknown email and wrong password, so the API cannot enumerate accounts. */
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final UserService userService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper mapper;
    private final CurrentActor currentActor;
    private final LoginRateLimiter loginRateLimiter;

    public AuthServiceImpl(UserService userService,
                           AccessTokenService accessTokenService,
                           RefreshTokenService refreshTokenService,
                           PasswordEncoder passwordEncoder,
                           AccountMapper mapper,
                           CurrentActor currentActor,
                           LoginRateLimiter loginRateLimiter) {
        this.userService = userService;
        this.accessTokenService = accessTokenService;
        this.refreshTokenService = refreshTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.currentActor = currentActor;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // M-12: refuse fast when the same email has failed too often in a short window.
        // The message deliberately mirrors an ordinary rate-limit response — it must not
        // leak whether the account exists.
        if (loginRateLimiter.isBlocked(request.email())) {
            throw new TooManyRequestsException(
                    "Too many login attempts. Try again in a few minutes.");
        }

        User user = userService.findByEmail(request.email()).orElse(null);
        if (user == null) {
            loginRateLimiter.recordFailure(request.email());
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(request.email());
            log.warn("Failed login for {}", user.getEmail());
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            // Do not count a disabled-account attempt against the same-shape limiter:
            // the account holder locking themselves out would be dumb, and no attacker
            // can distinguish "disabled" from "not found" without the correct password.
            throw new UnauthorizedException("This account has been disabled");
        }

        loginRateLimiter.reset(request.email());
        userService.recordSuccessfulLogin(user);
        return buildLoginResponse(user);
    }

    @Override
    public LoginResponse refresh(String presentedRefreshToken) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(presentedRefreshToken);
        AccessTokenService.IssuedToken access = accessTokenService.issue(rotated.user());
        return LoginResponse.bearer(
                access.token(),
                access.expiresInSeconds(),
                rotated.newRefreshToken(),
                rotated.expiresInSeconds(),
                mapper.toResponse(rotated.user()));
    }

    @Override
    public void logout(String presentedRefreshToken) {
        refreshTokenService.revoke(presentedRefreshToken);
    }

    @Override
    public UserResponse currentUser() {
        String userId = currentActor.userId()
                .orElseThrow(() -> new UnauthorizedException(
                        "This endpoint is for logged-in users; an API key has no user behind it"));
        return mapper.toResponse(userService.require(userId));
    }

    private LoginResponse buildLoginResponse(User user) {
        AccessTokenService.IssuedToken access = accessTokenService.issue(user);
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user);
        return LoginResponse.bearer(
                access.token(),
                access.expiresInSeconds(),
                refresh.token(),
                refresh.expiresInSeconds(),
                mapper.toResponse(user));
    }
}
