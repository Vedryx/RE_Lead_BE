package com.vedryxtech.voiceagent.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/** Access to the authenticated actor without static calls. */
@Component
public class CurrentActor {

    public Optional<String> userId() {
        return jwt().map(Jwt::getSubject);
    }

    public Optional<String> email() {
        return jwt().map(token -> token.getClaimAsString(SecurityClaims.CLAIM_EMAIL));
    }

    public String actor() {
        return userId().orElse("ai_agent");
    }

    public List<String> roles() {
        return authentication()
                .map(auth -> auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .map(authority -> authority.startsWith("ROLE_") ? authority.substring(5) : authority)
                        .toList())
                .orElse(List.of());
    }

    public boolean isApiClient() {
        return hasRole("API_CLIENT");
    }

    public boolean hasRole(String role) {
        return roles().contains(role);
    }

    private Optional<Jwt> jwt() {
        return authentication()
                .map(Authentication::getPrincipal)
                .filter(Jwt.class::isInstance)
                .map(Jwt.class::cast);
    }

    private Optional<Authentication> authentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                ? Optional.of(authentication)
                : Optional.empty();
    }
}
