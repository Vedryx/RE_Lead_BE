package com.vedryxtech.voiceagent.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for verifying that a webhook really came from LiveKit.
 *
 * <p>The same API key and secret the agent dials with. LiveKit signs each webhook
 * with the secret, so possessing it is what lets this service tell a real callback
 * from anyone who found the URL.
 */
@ConfigurationProperties(prefix = "app.livekit")
public class LiveKitProperties {

    private String apiKey = "";

    private String apiSecret = "";

    public boolean isConfigured() {
        return !apiKey.isBlank() && !apiSecret.isBlank();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }
}
