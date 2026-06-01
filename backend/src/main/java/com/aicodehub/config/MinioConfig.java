package com.aicodehub.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient() {
        String endpoint = System.getenv().getOrDefault("MINIO_URL", "http://minio:9000");
        String user = System.getenv().getOrDefault("MINIO_USER", "minioadmin");
        String pass = System.getenv().getOrDefault("MINIO_PASSWORD", "minioadmin123");
        return MinioClient.builder().endpoint(endpoint).credentials(user, pass).build();
    }
}
