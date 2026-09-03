package com.vedryxtech.voiceagent.webhook;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * This endpoint is unauthenticated by necessity — LiveKit holds no credential of
 * ours — so the signature is the only thing standing between a real callback and
 * anyone who found the URL.
 */
class LiveKitWebhookVerifierTest {

    // Long enough for HS256; a shorter secret is rejected by the signer itself.
    private static final String SECRET = "a-test-secret-long-enough-for-hmac-sha256-signing";
    private static final String BODY = "{\"event\":\"egress_ended\",\"egressInfo\":{}}";

    private LiveKitWebhookVerifier verifier;

    @BeforeEach
    void setUp() {
        LiveKitProperties properties = new LiveKitProperties();
        properties.setApiKey("APItest");
        properties.setApiSecret(SECRET);
        verifier = new LiveKitWebhookVerifier(properties);
    }

    @Test
    void a_properly_signed_delivery_is_accepted() throws Exception {
        assertThat(verifier.isAuthentic(sign(BODY, SECRET, +300), BODY)).isTrue();
    }

    @Test
    void a_bearer_prefix_is_tolerated() throws Exception {
        assertThat(verifier.isAuthentic("Bearer " + sign(BODY, SECRET, +300), BODY)).isTrue();
    }

    @Test
    void a_signature_from_someone_elses_secret_is_refused() throws Exception {
        String token = sign(BODY, "a-different-secret-also-long-enough-for-hmac-256", +300);

        assertThat(verifier.isAuthentic(token, BODY)).isFalse();
    }

    @Test
    void a_body_swapped_after_signing_is_refused() throws Exception {
        // The signature alone proves only that LiveKit sent something. Without the
        // digest check a captured header could be replayed against any payload.
        String token = sign(BODY, SECRET, +300);

        assertThat(verifier.isAuthentic(token, "{\"event\":\"egress_ended\",\"egressInfo\":{\"x\":1}}"))
                .isFalse();
    }

    @Test
    void an_expired_token_is_refused() throws Exception {
        assertThat(verifier.isAuthentic(sign(BODY, SECRET, -300), BODY)).isFalse();
    }

    @Test
    void a_token_with_no_digest_claim_is_refused() throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .issuer("APItest")
                        .expirationTime(new Date(System.currentTimeMillis() + 300_000))
                        .build());
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));

        assertThat(verifier.isAuthentic(jwt.serialize(), BODY)).isFalse();
    }

    @Test
    void nonsense_in_the_header_is_refused_rather_than_thrown() {
        for (String header : new String[]{null, "", "   ", "not-a-jwt", "a.b.c"}) {
            assertThat(verifier.isAuthentic(header, BODY)).as(String.valueOf(header)).isFalse();
        }
    }

    @Test
    void with_no_credentials_configured_everything_is_refused() throws Exception {
        var unconfigured = new LiveKitWebhookVerifier(new LiveKitProperties());

        assertThat(unconfigured.isAuthentic(sign(BODY, SECRET, +300), BODY)).isFalse();
    }

    /** Signs the way LiveKit does: HS256, with a base64 sha256 of the body. */
    private static String sign(String body, String secret, int expiresInSeconds) throws Exception {
        String digest = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder()
                        .issuer("APItest")
                        .claim("sha256", digest)
                        .expirationTime(new Date(System.currentTimeMillis() + expiresInSeconds * 1000L))
                        .build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
