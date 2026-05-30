package com.aicodehub.service;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

@Slf4j
@Service
public class MinioService {

    private final MinioClient client;
    private static final String BUCKET = "documents";
    private static final String IMAGES_BUCKET = "images";

    public MinioService() {
        String endpoint = System.getenv().getOrDefault("MINIO_URL", "http://minio:9000");
        String user = System.getenv().getOrDefault("MINIO_USER", "minioadmin");
        String pass = System.getenv().getOrDefault("MINIO_PASSWORD", "minioadmin123");
        this.client = MinioClient.builder().endpoint(endpoint).credentials(user, pass).build();
        try {
            for (String b : new String[]{BUCKET, IMAGES_BUCKET}) {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(b).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(b).build());
                    // Set public read policy for images bucket
                    if (IMAGES_BUCKET.equals(b)) {
                        String policy = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + b + "/*\"]}]}";
                        client.setBucketPolicy(io.minio.SetBucketPolicyArgs.builder().bucket(b).config(policy).build());
                    }
                    log.info("MinIO bucket {} created", b);
                }
            }
        } catch (Exception e) {
            log.error("MinIO init failed: {}", e.getMessage());
        }
    }

    /** Upload avatar to public images bucket, returns public URL */
    public String uploadAvatar(MultipartFile file) {
        try {
            String objectName = "avatars/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
            client.putObject(PutObjectArgs.builder()
                .bucket(IMAGES_BUCKET).object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
            return "/minio/" + IMAGES_BUCKET + "/" + objectName;
        } catch (Exception e) {
            log.error("Avatar upload failed: {}", e.getMessage());
            return null;
        }
    }

    public String upload(String objectName, MultipartFile file) {
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(BUCKET).object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());
            return getPresignedUrl(objectName);
        } catch (Exception e) {
            log.error("MinIO upload failed: {}", e.getMessage());
            return null;
        }
    }

    /** Return a public URL for the object via nginx proxy */
    public String getPublicUrl(String objectName) {
        return "/minio/" + BUCKET + "/" + objectName;
    }

    private String getPresignedUrl(String objectName) {
        return getPublicUrl(objectName);
    }

    public String upload(String objectName, InputStream stream, long size, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                .bucket(BUCKET).object(objectName)
                .stream(stream, size, -1)
                .contentType(contentType)
                .build());
            return BUCKET + "/" + objectName;
        } catch (Exception e) {
            log.error("MinIO upload failed: {}", e.getMessage());
            return null;
        }
    }
}
