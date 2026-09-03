package com.vedryxtech.voiceagent.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Credentials for the WhatsApp Cloud API (Meta Graph API).
 *
 * <p>Disabled by default. With no phone number and token configured, a lead can still be
 * flagged as wanting details ({@code detailsRequested}) — the request just sits unsent
 * instead of the CRM pretending it went out.
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public class WhatsAppProperties {

    private boolean enabled = false;

    /** Versioned per the Meta app's provisioned Graph API version — a bump is one line here. */
    private String apiBaseUrl = "https://graph.facebook.com/v25.0";

    /** The sending number's id, from the WhatsApp Business Platform app, not the phone number itself. */
    private String phoneNumberId;

    /** Permanent (system user) access token — the temporary one from the console expires in 24h. */
    private String accessToken;

    private Duration requestTimeout = Duration.ofSeconds(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }
}
