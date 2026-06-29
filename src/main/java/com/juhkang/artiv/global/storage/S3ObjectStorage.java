package com.juhkang.artiv.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 호환 오브젝트 스토리지(MinIO/R2/AWS S3). {@code app.storage.type=s3} 일 때 활성화.
 * 공개 URL은 버킷의 퍼블릭 베이스(또는 CDN) 기준으로 만든다.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;

    public S3ObjectStorage(S3Client s3,
                           @Value("${app.storage.s3.bucket}") String bucket,
                           @Value("${app.storage.s3.public-base-url}") String publicBaseUrl) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        s3.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    @Override
    public String urlFor(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
