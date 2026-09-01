package com.vedryxtech.voiceagent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Bound from {@code app.security.*} in application.yml. */
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {

    /** HMAC signing key for login tokens. At least 32 characters for HS256. */
    private String jwtSecret;

    private String issuer = "vedryxtech-voice-agent";

    private Duration accessTokenTtl = Duration.ofHours(12);

    /** The organization, admin and API key created on first start. */
    private Bootstrap bootstrap = new Bootstrap();

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    /**
     * Everything needed for a working installation on first boot. Nothing here overwrites data
     * that already exists, so these values only matter the first time the database is empty.
     */
    public static class Bootstrap {

        private boolean enabled = true;

        private String organizationName;

        private String organizationSlug;

        private String timezone = "Asia/Kolkata";

        private String adminEmail;

        private String adminPassword;

        private String adminName = "Administrator";

        /** The key the AI voice agent sends as {@code X-API-Key}. Installed once, then rotatable. */
        private String apiKey;

        /** Load the sample leads from {@code seed/leads.json} on start. */
        private boolean seedLeads = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getOrganizationName() {
            return organizationName;
        }

        public void setOrganizationName(String organizationName) {
            this.organizationName = organizationName;
        }

        public String getOrganizationSlug() {
            return organizationSlug;
        }

        public void setOrganizationSlug(String organizationSlug) {
            this.organizationSlug = organizationSlug;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public String getAdminEmail() {
            return adminEmail;
        }

        public void setAdminEmail(String adminEmail) {
            this.adminEmail = adminEmail;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getAdminName() {
            return adminName;
        }

        public void setAdminName(String adminName) {
            this.adminName = adminName;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public boolean isSeedLeads() {
            return seedLeads;
        }

        public void setSeedLeads(boolean seedLeads) {
            this.seedLeads = seedLeads;
        }
    }
}
