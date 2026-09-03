package com.vedryxtech.voiceagent.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where LiveKit tells us a recording is finished.
 *
 * <p>Unauthenticated by necessity — LiveKit holds no credential of ours — so the
 * signature on the body is the whole of the authentication. The raw string is taken
 * rather than a parsed object because the digest is over the exact bytes sent; letting
 * Jackson deserialise first would leave nothing to hash.
 */
@Tag(name = "9. Webhooks", description = "Callbacks from LiveKit. Not for humans.")
@RestController
@RequestMapping(path = "/api/v1/webhooks")
public class LiveKitWebhookController {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookController.class);

    private final LiveKitWebhookVerifier verifier;
    private final LiveKitWebhookService service;

    public LiveKitWebhookController(LiveKitWebhookVerifier verifier, LiveKitWebhookService service) {
        this.verifier = verifier;
        this.service = service;
    }

    @Operation(summary = "LiveKit egress and room events",
            description = "Verified by the JWT LiveKit signs each delivery with. Always answers "
                    + "200 for anything authentic, including events this service ignores — "
                    + "LiveKit retries whatever is not 2xx, and a retry storm over "
                    + "track_published helps nobody.")
    @SecurityRequirements
    @PostMapping(consumes = {"application/webhook+json", "application/json"})
    public ResponseEntity<String> receive(@RequestHeader(value = "Authorization", required = false)
                                          String authorization,
                                          @RequestBody String rawBody) {
        if (!verifier.isAuthentic(authorization, rawBody)) {
            // Deliberately terse. A caller who cannot sign does not get told which half
            // of the check failed.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unverified");
        }
        try {
            return ResponseEntity.ok(service.apply(rawBody));
        } catch (RuntimeException ex) {
            // Answering 500 earns a retry, which is right for a transient fault and
            // harmless otherwise: applying the same egress result twice is idempotent.
            log.error("Failed to apply webhook: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
