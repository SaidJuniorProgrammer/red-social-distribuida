package com.redsocial.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.file.Path;

@ApplicationScoped
public class S3StorageService {

    public static final String BUCKET_NAME = "red-social-media";

    @Inject
    S3Client s3Client;

    @ConfigProperty(name = "quarkus.s3.endpoint-override", defaultValue = "http://localhost:9090")
    String endpointOverride;

    public void setS3Client(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String subirArchivo(String postId, String fileName, String contentType, Path filePath) {
        asegurarBucketExiste();

        String nombreLimpio = fileName == null ? "archivo" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        String objectKey = postId + "/" + nombreLimpio;
        String mimeType = (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;

        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .contentType(mimeType)
                .build();

        s3Client.putObject(putRequest, RequestBody.fromFile(filePath));

        String base = endpointOverride.endsWith("/")
                ? endpointOverride.substring(0, endpointOverride.length() - 1)
                : endpointOverride;

        return base + "/" + BUCKET_NAME + "/" + objectKey;
    }

    private void asegurarBucketExiste() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(BUCKET_NAME).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
        }
    }
}