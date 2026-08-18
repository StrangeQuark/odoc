package com.strangequark.odoc.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest;

/** Proves the local S3-compatible harness without depending on a developer-installed MinIO. */
@Testcontainers(disabledWithoutDocker = true)
class MinioIntegrationTest {
    private static final String ACCESS_KEY = "minio-test-access";
    private static final String SECRET_KEY = "minio-test-secret";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2025-07-23T15-54-02Z"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void storesAndReadsAnObjectUsingTheS3CompatibilityContract() {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000)))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build()) {
            String isolatedKey = UUID.randomUUID().toString();
            String bucket = "odoc-phase0-" + isolatedKey;
            String key = "contract/" + isolatedKey + ".txt";
            s3.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            ObjectStorage storage = new S3ObjectStorage(s3, bucket);
            storage.put(key, "phase0 object round trip".getBytes(StandardCharsets.UTF_8), "application/octet-stream");

            String object = new String(storage.get(key), StandardCharsets.UTF_8);

            assertThat(object).isEqualTo("phase0 object round trip");
            assertThat(storage.list("contract/", 10)).extracting(ObjectStorage.StoredObject::key).containsExactly(key);
            storage.delete(key);
            s3.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build());
        }
    }
}
