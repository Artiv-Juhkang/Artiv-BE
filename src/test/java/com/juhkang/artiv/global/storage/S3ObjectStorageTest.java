package com.juhkang.artiv.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

/** S3 호환 스토리지(MinIO)에 실제로 저장·조회되는지 검증. */
@Testcontainers
class S3ObjectStorageTest {

    private static final String BUCKET = "test-bucket";

    @Container
    static MinIOContainer minio = new MinIOContainer(DockerImageName.parse("minio/minio:latest"));

    private S3Client s3;
    private S3ObjectStorage storage;

    @BeforeEach
    void setUp() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create(minio.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(minio.getUserName(), minio.getPassword())))
                .forcePathStyle(true)
                .build();
        if (s3.listBuckets().buckets().stream().noneMatch(b -> b.name().equals(BUCKET))) {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
        storage = new S3ObjectStorage(s3, BUCKET, "https://cdn.example.com/" + BUCKET);
    }

    @Test
    void put한_바이트가_S3에서_그대로_읽힌다() {
        byte[] content = "fake-image-bytes".getBytes();

        storage.put("1/1/0.png", content, "image/png");

        byte[] got = s3.getObjectAsBytes(
                GetObjectRequest.builder().bucket(BUCKET).key("1/1/0.png").build()).asByteArray();
        assertThat(got).isEqualTo(content);
    }

    @Test
    void urlFor는_publicBaseUrl_기준_경로다() {
        assertThat(storage.urlFor("1/1/0.png")).isEqualTo("https://cdn.example.com/test-bucket/1/1/0.png");
    }
}
