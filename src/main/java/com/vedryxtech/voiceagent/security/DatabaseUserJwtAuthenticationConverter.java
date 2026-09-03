package com.vedryxtech.voiceagent.security;

import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.user.domain.UserRole;
import com.vedryxtech.voiceagent.user.persistence.UserRepository;
import org.bson.types.ObjectId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;

/**
 * Turns a valid access token into an authenticated principal by looking the user up in the
 * database — it does <b>not</b> trust the token's {@code roles} claim.
 *
 * <p>Why this exists: the token is signed with a symmetric secret. Anyone who obtains that
 * secret (or an already-issued token) could otherwise forge {@code roles:["ADMIN"]} for any
 * — even nonexistent — subject and be granted full admin for the token's lifetime. By
 * re-reading the user on every request we:</p>
 *
 * <ul>
 *   <li>reject a token whose subject is not a real user (a forged token is useless);</li>
 *   <li>reject a token for a disabled (or deleted) user immediately, instead of honouring it
 *       until it expires;</li>
 *   <li>derive authorities from the user's <b>current</b> database role, so a stale
 *       {@code roles} claim cannot escalate privileges.</li>
 * </ul>
 *
 * <p>A missing, disabled, or unparseable subject raises {@link InvalidBearerTokenException},
 * which the resource server surfaces as {@code 401}. The lookup is one indexed read by
 * {@code _id} per request — negligible at this volume, and stateless (no session).</p>
 *
 * <p>This runs only on the {@code Authorization: Bearer} JWT path. The AI voice agent
 * authenticates with {@code X-API-Key} via {@link ApiKeyAuthenticationFilter}, which sets its
 * own {@code API_CLIENT} authentication before the JWT filter — so this converter never runs
 * for the agent and cannot affect its endpoints.</p>
 */
// Wired as a @Bean in SecurityConfig rather than a @Component on purpose: a component-scanned
// Converter is auto-detected by @WebMvcTest slices and registered into mvcConversionService, which
// would eagerly pull UserRepository (a Mongo bean) into Mongo-less web-slice contexts. Keeping it a
// SecurityConfig @Bean confines it to the full application context, exactly as the previous
// JwtAuthenticationConverter @Bean was.
public class DatabaseUserJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;

    public DatabaseUserJwtAuthenticationConverter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = loadActiveUser(jwt.getSubject());

        Collection<GrantedAuthority> authorities = user.getRoles().stream()
                .map(UserRole::authority)
                .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
                .toList();

        return new JwtAuthenticationToken(jwt, authorities, user.getIdAsString());
    }

    private User loadActiveUser(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new InvalidBearerTokenException("The token has no subject");
        }

        ObjectId id;
        try {
            id = new ObjectId(subject);
        } catch (IllegalArgumentException ex) {
            throw new InvalidBearerTokenException("The token subject is not a valid user id");
        }

        User user = userRepository.findById(id)
                .orElseThrow(() -> new InvalidBearerTokenException("The token's user no longer exists"));

        if (!user.isEnabled()) {
            throw new InvalidBearerTokenException("This user account is disabled");
        }

        return user;
    }
}
