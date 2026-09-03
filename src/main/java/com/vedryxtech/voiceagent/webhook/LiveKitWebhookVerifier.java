package com.vedryxtech.voiceagent.webhook;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.ParseException;
import java.util.Base64;
import java.util.Date;

/**
 * Decides whether a webhook actually came from LiveKit.
 *
 * <p>LiveKit signs each delivery with a JWT in the Authorization header, carrying a
 * {@code sha256} claim holding a base64 digest of the request body. Two things have
 * to hold: the signature must verify against our API secret, and that digest must
 * match the bytes we received. The signature alone proves only that LiveKit sent
 * <em>something</em> — without the body check, a captured header could be replayed
 * against a different payload.
 *
 * <p>This endpoint is necessarily public: LiveKit cannot present our JWT or API key,
 * so the signature is the whole of the authentication. It is checked here rather than
 * in the security filter chain because Spring's resource server would try to validate
 * LiveKit's token as one of ours and reject it before this code ever ran.
 */
@Component
public class LiveKitWebhookVerifier {

    private static final Logger log = LoggerFactory.getLogger(LiveKitWebhookVerifier.class);

    private final LiveKitProperties properties;

    public LiveKitWebhookVerifier(LiveKitProperties properties) {
        this.properties = properties;
    }

    /** True when this body was signed by LiveKit with our secret and has not been altered. */
    public boolean isAuthentic(String authorization, String rawBody) {
        if (!properties.isConfigured()) {
            log.error("LiveKit credentials are not configured; refusing every webhook");
            return false;
        }
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        String token = authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : authorization.trim();

        try {
            SignedJWT jwt = SignedJWT.parse(token);

            JWSVerifier verifier = new MACVerifier(
                    properties.getApiSecret().getBytes(StandardCharsets.UTF_8));
            if (!jwt.verify(verifier)) {
                log.warn("Webhook signature did not verify");
                return false;
            }

            var claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            if (expiry != null && expiry.before(new Date())) {
                log.warn("Webhook token expired at {}", expiry);
                return false;
            }

            Object claimed = claims.getClaim("sha256");
            if (claimed == null) {
                log.warn("Webhook token carries no sha256 claim");
                return false;
            }

            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawBody.getBytes(StandardCharsets.UTF_8));
            String actual = Base64.getEncoder().encodeToString(digest);

            // Constant-time: a timing-sensitive comparison here would leak the expected
            // digest one byte at a time to anyone willing to send enough requests.
            if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                    claimed.toString().getBytes(StandardCharsets.UTF_8))) {
                log.warn("Webhook body does not match the digest it was signed with");
                return false;
            }
            return true;
        } catch (ParseException | RuntimeException | java.security.NoSuchAlgorithmException
                 | com.nimbusds.jose.JOSEException ex) {
            log.warn("Could not verify webhook: {}", ex.getMessage());
            return false;
        }
    }
}
