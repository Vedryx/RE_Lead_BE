package com.vedryxtech.voiceagent.security;

import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.user.persistence.UserRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * The access-token path must trust the database, not the {@code roles} claim. These tests pin
 * the four cases that matter: a forged token for a nonexistent user, a token for a disabled
 * user, a stale-claim escalation attempt, and the ordinary admin/member happy paths.
 */
class DatabaseUserJwtAuthenticationConverterTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final DatabaseUserJwtAuthenticationConverter converter =
            new DatabaseUserJwtAuthenticationConverter(userRepository);

    @Test
    void forgedTokenForANonexistentUserIsRejected() {
        ObjectId ghost = new ObjectId("000000000000000000000000");
        given(userRepository.findById(ghost)).willReturn(Optional.empty());

        // A validly-signed token could carry any subject + any roles claim; the DB has final say.
        Jwt forged = tokenFor(ghost.toHexString(), List.of("ADMIN"));

        assertThatThrownBy(() -> converter.convert(forged))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void tokenForADisabledUserIsRejected() {
        User disabled = user(UserRole.ADMIN);
        disabled.setEnabled(false);
        given(userRepository.findById(disabled.getId())).willReturn(Optional.of(disabled));

        Jwt token = tokenFor(disabled.getIdAsString(), List.of("ADMIN"));

        assertThatThrownBy(() -> converter.convert(token))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void databaseRoleWinsOverAStaleAdminClaim() {
        User member = user(UserRole.MEMBER);
        given(userRepository.findById(member.getId())).willReturn(Optional.of(member));

        // Token still claims ADMIN (issued before a demotion, or forged); DB says MEMBER.
        Jwt staleAdminClaim = tokenFor(member.getIdAsString(), List.of("ADMIN"));

        AbstractAuthenticationToken auth = converter.convert(staleAdminClaim);

        assertThat(authorities(auth)).containsExactly("ROLE_MEMBER");
        assertThat(authorities(auth)).doesNotContain("ROLE_ADMIN");
    }

    @Test
    void ordinaryAdminAndMemberTokensAuthenticateWithTheirDatabaseRole() {
        User admin = user(UserRole.ADMIN);
        User member = user(UserRole.MEMBER);
        given(userRepository.findById(admin.getId())).willReturn(Optional.of(admin));
        given(userRepository.findById(member.getId())).willReturn(Optional.of(member));

        assertThat(authorities(converter.convert(tokenFor(admin.getIdAsString(), List.of("ADMIN")))))
                .containsExactly("ROLE_ADMIN");
        assertThat(authorities(converter.convert(tokenFor(member.getIdAsString(), List.of("MEMBER")))))
                .containsExactly("ROLE_MEMBER");
    }

    @Test
    void aTokenWithoutASubjectIsRejected() {
        Jwt noSubject = Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        assertThatThrownBy(() -> converter.convert(noSubject))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void aMalformedSubjectIsRejected() {
        Jwt malformed = tokenFor("not-an-object-id", List.of("ADMIN"));

        assertThatThrownBy(() -> converter.convert(malformed))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private static List<String> authorities(AbstractAuthenticationToken auth) {
        return auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    private static User user(UserRole role) {
        User user = new User();
        user.setId(new ObjectId());
        user.setEmail(role.name().toLowerCase() + "@example.com");
        user.setRoles(Set.of(role));
        user.setEnabled(true);
        return user;
    }

    private static Jwt tokenFor(String subject, List<String> rolesClaim) {
        return Jwt.withTokenValue("t")
                .header("alg", "HS256")
                .subject(subject)
                .claim("roles", rolesClaim)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();
    }
}
