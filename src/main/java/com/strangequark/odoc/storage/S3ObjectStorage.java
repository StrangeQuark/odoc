package com.strangequark.odoc.storage;

import java.util.Objects;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/** S3-compatible implementation used only behind Odoc's authenticated gateway. */
final class S3ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final String bucket;

    S3ObjectStorage(S3Client client, String bucket) {
        this.client = Objects.requireNonNull(client, "client");
        this.bucket = Objects.requireNonNull(bucket, "bucket");
    }

    @Override
    public void put(String key, byte[] ciphertext, String contentType) {
        try {
            client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                    RequestBody.fromBytes(ciphertext));
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object storage write failed.", exception);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toBytes()).asByteArray();
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object storage read failed.", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object storage deletion failed.", exception);
        }
    }

    @Override
    public List<StoredObject> list(String prefix, int limit) {
        try {
            return client.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix)
                            .maxKeys(Math.max(1, Math.min(limit, 1_000))).build())
                    .stream().flatMap(page -> page.contents().stream()).limit(Math.max(1, limit))
                    .map(object -> new StoredObject(object.key(), object.lastModified()))
                    .toList();
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object storage listing failed.", exception);
        }
    }
}
