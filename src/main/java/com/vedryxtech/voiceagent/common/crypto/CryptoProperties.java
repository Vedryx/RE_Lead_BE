package com.vedryxtech.voiceagent.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound from {@code app.crypto.*}. The key that encrypts phone numbers at rest.
 *
 * <p>The key never belongs in this repository: set {@code PHONE_ENC_KEY} in the
 * environment. Lose it and every stored number becomes unreadable, so it is worth
 * treating like a database backup — rotating it needs a re-encryption pass over
 * every document, not just a new value here.</p>
 */
@ConfigurationProperties(prefix = "app.crypto")
public class CryptoProperties {

    /**
     * Base64 of 32 raw bytes (AES-256). Blank turns encryption off and leaves numbers
     * in plaintext, which is the only way an existing database keeps working before
     * the migration has run.
     */
    private String phoneKey = "";

    public String getPhoneKey() {
        return phoneKey;
    }

    public void setPhoneKey(String phoneKey) {
        this.phoneKey = phoneKey;
    }
}
