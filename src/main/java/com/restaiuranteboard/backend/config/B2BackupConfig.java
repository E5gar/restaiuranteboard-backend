package com.restaiuranteboard.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

@Configuration
public class B2BackupConfig {

    @Value("${app.backup.b2-key-id}")
    private String keyId;

    @Value("${app.backup.b2-app-key}")
    private String appKey;

    @Value("${app.backup.b2-endpoint}")
    private String endpoint;

    @Bean
    public S3Client b2S3Client() {
        String ep = endpoint != null ? endpoint.trim() : "";
        if (!ep.startsWith("http://") && !ep.startsWith("https://")) {
            ep = "https://" + ep;
        }
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(keyId, appKey)))
                .endpointOverride(URI.create(ep))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}

