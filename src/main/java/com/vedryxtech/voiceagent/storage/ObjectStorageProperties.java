package com.vedryxtech.voiceagent.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Where call artifacts live, and for how long a link to one stays valid.
 *
 * <p>Disabled by default. With no bucket configured the CRM still records calls and
 * transcripts in Mongo — it simply has no archive to point at, which is the correct
 * behaviour for a developer who has not been given R2 credentials.
 */
@ConfigurationProperties(prefix = "app.storage")
public class ObjectStorageProperties {

    private boolean enabled = false;

    /** {@code https://<ACCOUNT_ID>.r2.cloudflarestorage.com} for Cloudflare R2. */
    private String endpoint;

    /** R2 aliases empty and {@code us-east-1} to {@code auto}; any S3 client needs one. */
    private String region = "auto";

    private String bucket;

    private String accessKey;

    private String secretKey;

    /** Everything lives under this, so one bucket can hold more than recordings. */
    private String prefix = "recordings";

    /**
     * How long a minted link stays valid.
     *
     * <p>Long enough to press play, short enough that a URL pasted into a chat is dead
     * before it travels. A presigned URL is a bearer token: whoever holds it can play the
     * recording with no further authentication.
     */
    private Duration linkTtl = Duration.ofMinutes(15);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(String bucket) {
        this.bucket = bucket;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public Duration getLinkTtl() {
        return linkTtl;
    }

    public void setLinkTtl(Duration linkTtl) {
        this.linkTtl = linkTtl;
    }
}
