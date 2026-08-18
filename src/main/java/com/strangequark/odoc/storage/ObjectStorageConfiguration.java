package com.strangequark.odoc.storage;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ObjectStorageProperties.class)
class ObjectStorageConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(ObjectStorage.class)
    ObjectStorage objectStorage(ObjectStorageProperties properties) {
        if (!properties.enabled()) return new DisabledObjectStorage();
        URI endpoint = endpoint(properties);
        S3Client client = S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.of(required(properties.region(), "region")))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required(properties.accessKey(), "access key"), required(properties.secretKey(), "secret key"))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(properties.pathStyle()).build())
                .build();
        return new ClosingObjectStorage(new S3ObjectStorage(client, required(properties.bucket(), "bucket")), client);
    }

    private static URI endpoint(ObjectStorageProperties properties) {
        try {
            URI endpoint = URI.create(required(properties.endpoint(), "endpoint"));
            if (endpoint.getHost() == null || (!"https".equals(endpoint.getScheme()) && !"http".equals(endpoint.getScheme()))) {
                throw new IllegalArgumentException("Object storage endpoint must be an absolute HTTP(S) URL.");
            }
            if (properties.requireTls() && !"https".equals(endpoint.getScheme())) {
                throw new IllegalStateException("Object storage requires an HTTPS endpoint outside the local profile.");
            }
            return endpoint;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid Odoc object-storage endpoint.", exception);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException("Object storage " + name + " is required when enabled.");
        return value;
    }

    private static final class DisabledObjectStorage implements ObjectStorage {
        @Override public void put(String key, byte[] ciphertext, String contentType) { unavailable(); }
        @Override public byte[] get(String key) { unavailable(); return null; }
        @Override public void delete(String key) { unavailable(); }
        @Override public java.util.List<StoredObject> list(String prefix, int limit) { unavailable(); return java.util.List.of(); }
        private static void unavailable() { throw new ObjectStorageException("Object storage is not configured.", null); }
    }

    /** Lets Spring close the SDK client without exposing it beyond this configuration. */
    private record ClosingObjectStorage(ObjectStorage delegate, S3Client client) implements ObjectStorage, AutoCloseable {
        @Override public void put(String key, byte[] ciphertext, String contentType) { delegate.put(key, ciphertext, contentType); }
        @Override public byte[] get(String key) { return delegate.get(key); }
        @Override public void delete(String key) { delegate.delete(key); }
        @Override public java.util.List<StoredObject> list(String prefix, int limit) { return delegate.list(prefix, limit); }
        @Override public void close() { client.close(); }
    }
}
