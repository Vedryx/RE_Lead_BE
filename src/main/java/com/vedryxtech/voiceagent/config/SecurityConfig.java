package com.vedryxtech.voiceagent.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.common.error.ApiErrorResponseWriter;
import com.vedryxtech.voiceagent.security.ApiKeyAuthenticationFilter;
import com.vedryxtech.voiceagent.security.DatabaseUserJwtAuthenticationConverter;
import com.vedryxtech.voiceagent.user.persistence.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Two ways in, both stateless:
 *
 * <ul>
 *   <li><b>People</b> log in and send {@code Authorization: Bearer <jwt>}.</li>
 *   <li><b>The AI voice agent</b> sends {@code X-API-Key: vdx_...} and needs no login.</li>
 * </ul>
 *
 * <p>The API-key filter runs first and simply passes the request on when no key is present,
 * so the two never interfere with each other.</p>
 *
 * <p>Single-tenant: the pre-rework self-serve {@code POST /api/v1/organizations} signup is
 * gone, so nothing under {@code /organizations} is public any more. Only the three auth
 * endpoints (login, refresh, logout) and the Swagger UI plumbing are open.</p>
 */
@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
@EnableMethodSecurity
public class SecurityConfig {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecurityProperties properties;

    public SecurityConfig(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ApiErrorResponseWriter apiErrorResponseWriter,
                                           ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                                           DatabaseUserJwtAuthenticationConverter jwtAuthenticationConverter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(apiKeyAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        // Swagger UI itself is public; the endpoints it calls still need credentials.
                        .requestMatchers("/docs", "/docs/**", "/swagger-ui.html", "/swagger-ui/**",
                                "/scalar", "/scalar/**", "/webjars/**",
                                "/v3/api-docs", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // LiveKit holds no credential of ours; the signature on the
                        // body is the authentication, checked in the controller.
                        .requestMatchers("/api/v1/webhooks/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, ex) ->
                                apiErrorResponseWriter.write(response, request.getRequestURI(),
                                        HttpStatus.UNAUTHORIZED,
                                        "Log in and send an Authorization: Bearer token, "
                                                + "or send an X-API-Key header"))
                        .accessDeniedHandler((request, response, ex) ->
                                apiErrorResponseWriter.write(response, request.getRequestURI(),
                                        HttpStatus.FORBIDDEN,
                                        "You do not have access to this resource")));

        return http.build();
    }

    /**
     * Authorities come from the database user, not the token's {@code roles} claim, so a forged or
     * stale token cannot escalate privileges and a deleted/disabled user loses access immediately.
     */
    @Bean
    public DatabaseUserJwtAuthenticationConverter jwtAuthenticationConverter(UserRepository userRepository) {
        return new DatabaseUserJwtAuthenticationConverter(userRepository);
    }

    @Bean
    public ApiKeyAuthenticationFilter apiKeyAuthenticationFilter(@Lazy ApiKeyService apiKeyService,
                                                                 ApiErrorResponseWriter apiErrorResponseWriter) {
        return new ApiKeyAuthenticationFilter(apiKeyService, apiErrorResponseWriter);
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey().getEncoded()));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private SecretKeySpec secretKey() {
        byte[] keyBytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt-secret must be at least " + MIN_SECRET_BYTES + " characters for HS256");
        }
        return new SecretKeySpec(keyBytes, "HmacSHA256");
    }
}
