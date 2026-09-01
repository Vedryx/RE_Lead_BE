package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.user.api.dto.CreateUserRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginRequest;
import com.vedryxtech.voiceagent.auth.api.dto.LoginResponse;
import com.vedryxtech.voiceagent.organization.api.dto.RegisterOrganizationRequest;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import com.vedryxtech.voiceagent.exception.UnauthorizedException;
import com.vedryxtech.voiceagent.user.mapper.AccountMapper;
import com.vedryxtech.voiceagent.security.CurrentActor;
import com.vedryxtech.voiceagent.auth.application.AccessTokenService;
import com.vedryxtech.voiceagent.auth.application.AuthService;
import com.vedryxtech.voiceagent.organization.application.OrganizationService;
import com.vedryxtech.voiceagent.user.application.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    /** Same message for unknown email and wrong password, so the API cannot enumerate accounts. */
    private static final String BAD_CREDENTIALS = "Invalid email or password";

    private final OrganizationService organizationService;
    private final UserService userService;
    private final AccessTokenService accessTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AccountMapper mapper;
    private final CurrentActor currentActor;

    public AuthServiceImpl(OrganizationService organizationService,
                           UserService userService,
                           AccessTokenService accessTokenService,
                           PasswordEncoder passwordEncoder,
                           AccountMapper mapper,
                           CurrentActor currentActor) {
        this.organizationService = organizationService;
        this.userService = userService;
        this.accessTokenService = accessTokenService;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
        this.currentActor = currentActor;
    }

    @Override
    public LoginResponse registerOrganization(RegisterOrganizationRequest request) {
        Organization organization = organizationService.create(request);

        CreateUserRequest adminRequest = new CreateUserRequest(
                request.adminEmail(),
                request.adminPassword(),
                request.adminFullName(),
                request.contactPhone(),
                Set.of(UserRole.ORG_ADMIN));
        User admin = userService.create(organization.getIdAsString(), adminRequest);

        log.info("Registered organization {} with admin {}", organization.getSlug(), admin.getEmail());
        return buildLoginResponse(admin, organization);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userService.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(BAD_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.warn("Failed login for {}", user.getEmail());
            throw new UnauthorizedException(BAD_CREDENTIALS);
        }
        if (!user.isEnabled()) {
            throw new UnauthorizedException("This account has been disabled");
        }

        Organization organization = organizationService.require(user.getOrganizationId());
        if (organization.getStatus() != null && !organization.getStatus().canLogIn()) {
            throw new UnauthorizedException("This organization is suspended");
        }

        userService.recordSuccessfulLogin(user);
        return buildLoginResponse(user, organization);
    }

    @Override
    public UserResponse currentUser() {
        String userId = currentActor.userId()
                .orElseThrow(() -> new UnauthorizedException(
                        "This endpoint is for logged-in users; an API key has no user behind it"));
        return mapper.toResponse(userService.require(userId));
    }

    private LoginResponse buildLoginResponse(User user, Organization organization) {
        AccessTokenService.IssuedToken token = accessTokenService.issue(user, organization);
        return LoginResponse.bearer(
                token.token(),
                token.expiresInSeconds(),
                mapper.toResponse(user),
                mapper.toResponse(organization));
    }
}
