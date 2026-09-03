package com.vedryxtech.voiceagent.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes the two files a call leaves behind.
 *
 * <p>Only created when {@code app.storage.enabled} is true. Everything that uses it goes
 * through {@link CallArtifactService}, which works with or without it — a CRM with no
 * bucket configured still records calls, it just has no archive to point at.
 */
@Component
@ConditionalOnProperty(name = "app.storage.enabled", havingValue = "true")
public class CallArtifactStore {

    private static final Logger log = LoggerFactory.getLogger(CallArtifactStore.class);

    private final ObjectStorageProperties properties;
    private final S3Client client;
    private final S3Presigner presigner;

    public CallArtifactStore(ObjectStorageProperties properties) {
        this.properties = properties;
        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey()));
        var region = Region.of(properties.getRegion());
        var endpoint = URI.create(properties.getEndpoint());

        // R2 is not AWS: the bucket belongs in the path, not in a subdomain. LiveKit's
        // egress config needs the same setting, under the name force_path_style.
        var s3Config = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
                .credentialsProvider(credentials).region(region)
                .endpointOverride(endpoint).serviceConfiguration(s3Config).build();
        this.presigner = S3Presigner.builder()
                .credentialsProvider(credentials).region(region)
                .endpointOverride(endpoint).serviceConfiguration(s3Config).build();
        log.info("Call artifacts go to {} in bucket {}", properties.getEndpoint(),
                properties.getBucket());
    }

    /** Store one JSON document. Used for the transcript; the audio is written by egress. */
    public void putJson(String key, String json) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType("application/json; charset=utf-8")
                        .build(),
                RequestBody.fromString(json, StandardCharsets.UTF_8));
    }

    /**
     * Store arbitrary bytes. Used for the brochures and photos the "send details on
     * WhatsApp" flow hands out, which are uploaded rather than generated.
     */
    public void putObject(String key, byte[] content, String contentType) {
        client.putObject(PutObjectRequest.builder()
                        .bucket(properties.getBucket())
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(content));
    }

    /**
     * A link that plays this object, valid for the configured window.
     *
     * <p>The result is a bearer token in URL form: anyone holding it can fetch the object
     * until it expires, with no further authentication. Never log it, never store it,
     * never put it in an email.
     */
    public String presignedGet(String key) {
        var request = GetObjectPresignRequest.builder()
                .signatureDuration(properties.getLinkTtl())
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(properties.getBucket()).key(key).build())
                .build();
        return presigner.presignGetObject(request).url().toString();
    }

    /**
     * What is really under a prefix.
     *
     * <p>Worth asking rather than assuming. An egress that failed leaves the transcript
     * with no audio beside it, and a lifecycle rule that has run leaves neither.
     */
    public List<String> list(String prefix) {
        return client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(properties.getBucket()).prefix(prefix).maxKeys(100).build())
                .contents().stream().map(S3Object::key).toList();
    }

    public Optional<Long> sizeOf(String key) {
        return list(key).isEmpty() ? Optional.empty()
                : client.listObjectsV2(ListObjectsV2Request.builder()
                                .bucket(properties.getBucket()).prefix(key).maxKeys(1).build())
                        .contents().stream().findFirst().map(S3Object::size);
    }

    @PreDestroy
    void close() {
        client.close();
        presigner.close();
    }
}
