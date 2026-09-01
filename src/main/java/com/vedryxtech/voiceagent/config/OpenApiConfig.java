package com.vedryxtech.voiceagent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger UI at {@code /swagger-ui.html}.
 *
 * <p>The description doubles as the instructions for getting a credential, since everything
 * except login and organization signup needs one.</p>
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";
    private static final String API_KEY_SCHEME = "apiKeyAuth";

    private static final String DESCRIPTION = """
            Backend for the AI voice-agent dashboard: leads, outbound calls with automatic
            retries, call recordings, and the numbers the dashboard charts.

            ## Two ways to get in

            **You, in this page** - log in and use the token:

            1. Open **POST /api/v1/auth/login**, click *Try it out*, *Execute*.
               The example is already filled in with the default admin.
            2. Copy the `accessToken` from the response.
            3. Click the green **Authorize** button at the top right, paste it under
               **bearerAuth**, Authorize, Close.

            Every endpoint on this page now works. The token lasts 12 hours.

            **The AI voice agent** - no login, just its key:

            It sends `X-API-Key: vdx_...` on every request and can read and store leads and
            call outcomes. To try that here, paste the key under **apiKeyAuth** in the same
            Authorize dialog instead. An admin creates and rotates it under
            *API Keys*.

            ## What talks to what

            - **Leads** - the people to call. One record per phone number.
            - **Calls** - one record per attempt to reach them, kept forever as history.
            - **Dashboard** - the counts and charts, built from the two above.
            """;

    /**
     * Makes the documented field names match the ones the API actually accepts.
     *
     * <p>Jackson and springdoc must agree on field names: springdoc builds its schemas with its own
     * mapper, so it is pointed at the application mapper here. Otherwise the documented names
     * can drift from the accepted ones, and since unknown fields are ignored on binding a payload
     * copied out of Swagger would silently lose values instead of failing loudly.</p>
     */
    @Bean
    public ModelResolver modelResolver(ObjectMapper objectMapper) {
        return new ModelResolver(objectMapper);
    }

    @Bean
    public OpenAPI voiceAgentOpenApi(@Value("${server.port:8080}") String port) {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Voice Agent - Dashboard API")
                        .version("v1")
                        .description(DESCRIPTION)
                        .contact(new Contact().name("Vedryx Tech")))
                .servers(List.of(new Server()
                        .url("http://localhost:" + port)
                        .description("Local")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken returned by POST /api/v1/auth/login"))
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-API-Key")
                                .description("The key the AI voice agent uses. "
                                        + "Create one with POST /api/v1/api-keys/current")))
                // Either credential opens any protected endpoint; the public ones opt out
                // with @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
