package com.vedryxtech.voiceagent.security;

import com.vedryxtech.voiceagent.apikey.application.ApiKeyService;
import com.vedryxtech.voiceagent.common.error.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lets the voice agent authenticate with an API key instead of a login.
 *
 * <p>Single-tenant: the key does not scope anything, it just proves the caller is the voice
 * agent. Match means the principal is {@code "ai_agent"} with a single {@code API_CLIENT}
 * authority; the {@link CurrentActor#actor()} audit trail continues to attribute writes to
 * {@code "ai_agent"}.</p>
 *
 * <p>Runs before the JWT filter. A request carrying no API key falls straight through, so a
 * normal {@code Authorization: Bearer} login is unaffected.</p>
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    public static final String PRINCIPAL = "ai_agent";
    public static final String ROLE = "API_CLIENT";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private final ApiKeyService apiKeyService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService,
                                      ApiErrorResponseWriter apiErrorResponseWriter) {
        this.apiKeyService = apiKeyService;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!apiKeyService.matches(apiKey)) {
            log.warn("Rejected an unknown API key on {} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response, request.getRequestURI());
            return;
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                PRINCIPAL,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String path) throws IOException {
        SecurityContextHolder.clearContext();
        apiErrorResponseWriter.write(
                response,
                path,
                HttpStatus.UNAUTHORIZED,
                "The API key in the " + HEADER + " header is not valid");
    }
}
