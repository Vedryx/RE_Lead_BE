package com.vedryxtech.voiceagent.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends one WhatsApp message via the Cloud API (Meta Graph API).
 *
 * <p>Only created when {@code app.whatsapp.enabled} is true, the same guard
 * {@code CallArtifactStore} uses for object storage — everything that calls this goes through
 * {@link WhatsAppNotificationService}, which works with or without it.
 *
 * <p>A message sent this way — business-initiated, outside a live chat with the lead — must
 * use an approved message template once one exists; sending {@code document}/{@code image}
 * directly (as done here) only reaches numbers inside Meta's test list until the WhatsApp
 * Business Account is verified and a template is approved. Swap the payload building in
 * {@link #send(String, Map)}'s callers for a template payload when that's ready — the
 * transport here does not need to change.
 */
@Component
@ConditionalOnProperty(name = "app.whatsapp.enabled", havingValue = "true")
public class WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppClient.class);

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    public WhatsAppClient(WhatsAppProperties properties) {
        this.properties = properties;

        var requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMs = (int) properties.getRequestTimeout().toMillis();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = RestClient.builder()
                .baseUrl(properties.getApiBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getAccessToken())
                .build();
        log.info("WhatsApp sending is on, via phone number id {}", properties.getPhoneNumberId());
    }

    /** A document message — the brochure. {@code link} must be reachable by Meta's servers. */
    public void sendDocument(String toPhone, String link, String filename) {
        log.info("Sending document '{}' to {} via link: {}", filename, toPhone, link);
        Map<String, Object> media = new LinkedHashMap<>();
        media.put("link", link);
        if (filename != null && !filename.isBlank()) {
            media.put("filename", filename);
        }
        send(toPhone, Map.of("type", "document", "document", media));
    }

    /** An image message — one photo. WhatsApp allows exactly one image per message. */
    public void sendImage(String toPhone, String link) {
        send(toPhone, Map.of("type", "image", "image", Map.of("link", link)));
    }

    /** A plain text message — used when there is nothing to attach. */
    public void sendText(String toPhone, String body) {
        send(toPhone, Map.of("type", "text", "text", Map.of("body", body)));
    }

    private void send(String toPhone, Map<String, Object> typed) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", toPhone);
        body.putAll(typed);

        // Meta returning 200 only means the message was accepted into its queue, not that
        // it was delivered — actual delivery failures show up later, on a status webhook
        // we don't have wired up. Logging the accepted response is the closest visibility
        // into that we get without one: at minimum it proves the request shape and the
        // media link were valid enough for Meta to accept.
        String response = restClient.post()
                .uri("/{phoneNumberId}/messages", properties.getPhoneNumberId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        log.info("WhatsApp accepted a {} message to {}: {}", typed.get("type"), toPhone, response);
    }
}
