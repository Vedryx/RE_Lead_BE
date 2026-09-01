package com.vedryxtech.voiceagent.auth.application;

import com.vedryxtech.voiceagent.config.SecurityProperties;
import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.security.SecurityClaims;
import com.vedryxtech.voiceagent.auth.application.AccessTokenService;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Issues the HS256 access token. The organization id travels in the {@code org_id} claim, which
 * is the only thing the service layer trusts when scoping queries to a tenant.
 */
@Service
public class AccessTokenServiceImpl implements AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final SecurityProperties properties;

    public AccessTokenServiceImpl(JwtEncoder jwtEncoder, SecurityProperties properties) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    @Override
    public IssuedToken issue(User user, Organization organization) {
        Instant now = Instant.now();
        long ttlSeconds = properties.getAccessTokenTtl().toSeconds();

        List<String> roles = user.getRoles().stream().map(UserRole::name).toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(user.getIdAsString())
                .claim("org_slug", organization.getSlug())
                .claim(SecurityClaims.CLAIM_EMAIL, user.getEmail())
                .claim(SecurityClaims.CLAIM_NAME, user.getFullName())
                .claim(SecurityClaims.CLAIM_ROLES, roles)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttlSeconds);
    }
}
