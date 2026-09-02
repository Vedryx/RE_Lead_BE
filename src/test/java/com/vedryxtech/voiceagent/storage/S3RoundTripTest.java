package com.vedryxtech.voiceagent.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the path-style + presigned-URL combination against a real S3 API rather than
 * a mock. R2 is not reachable from a test run, but it speaks the same protocol, and
 * the two things worth proving — that path-style addressing works and that a presigned
 * GET is actually fetchable — are protocol-level.
 *
 * <p>Opt in with {@code -Ds3.roundtrip=true} and a MinIO on :9010.
 */
@EnabledIfSystemProperty(named = "s3.roundtrip", matches = "true")
class S3RoundTripTest {

    private CallArtifactStore store;

    @BeforeEach
    void setUp() {
        var properties = new ObjectStorageProperties();
        properties.setEnabled(true);
        properties.setEndpoint("http://localhost:9010");
        properties.setRegion("us-east-1");
        properties.setBucket("calls");
        properties.setAccessKey("testkey");
        properties.setSecretKey("testsecret123");
        properties.setLinkTtl(Duration.ofMinutes(15));

        S3Client admin = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("testkey", "testsecret123")))
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create("http://localhost:9010"))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
        try {
            admin.createBucket(CreateBucketRequest.builder().bucket("calls").build());
        } catch (RuntimeException alreadyThere) {
            // fine
        }
        store = new CallArtifactStore(properties);
    }

    @Test
    void a_transcript_written_under_a_prefix_is_listed_and_fetchable() throws Exception {
        String prefix = "recordings/my-home-sanctuary/2026/09/abc123/";
        String key = prefix + "transcript.json";

        store.putJson(key, "{\"turns\":[{\"role\":\"lead\",\"text\":\"haan boliye\"}]}");

        assertThat(store.list(prefix)).containsExactly(key);

        String url = store.presignedGet(key);
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("haan boliye");
    }

    @Test
    void the_prefix_itself_is_not_something_that_can_be_opened() throws Exception {
        // The reason Mongo stores the prefix and mints links per object: there is no
        // such thing as a folder, and a presigned URL signs exactly one key.
        store.putJson("recordings/p/2026/09/xyz/transcript.json", "{}");

        String url = store.presignedGet("recordings/p/2026/09/xyz/");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isNotEqualTo(200);
    }

    @Test
    void an_unsigned_url_is_refused() throws Exception {
        store.putJson("recordings/p/2026/09/xyz/transcript.json", "{\"secret\":true}");

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(
                        "http://localhost:9010/calls/recordings/p/2026/09/xyz/transcript.json")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403);
    }
}
