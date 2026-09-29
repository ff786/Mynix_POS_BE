package com.mynix.backend.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Media storage over the S3 API: Cloudflare R2 in production, MinIO in the
 * local stack. Settings: MEDIA_STORAGE_ENDPOINT, MEDIA_STORAGE_BUCKET,
 * MEDIA_STORAGE_ACCESS_KEY, MEDIA_STORAGE_SECRET_KEY, MEDIA_PUBLIC_BASE_URL.
 */
@Slf4j
@Component
public class S3MediaStorage implements MediaStorage {

    private final String bucket;
    private final String publicBaseUrl;
    private final S3Client client;

    public S3MediaStorage(
            @Value("${mynix.media.endpoint:}") String endpoint,
            @Value("${mynix.media.bucket:}") String bucket,
            @Value("${mynix.media.access-key:}") String accessKey,
            @Value("${mynix.media.secret-key:}") String secretKey,
            @Value("${mynix.media.public-base-url:}") String publicBaseUrl) {

        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        boolean complete = !endpoint.isBlank() && !bucket.isBlank() && !accessKey.isBlank()
                && !secretKey.isBlank() && !publicBaseUrl.isBlank();

        this.client = complete
                ? S3Client.builder()
                        .endpointOverride(URI.create(endpoint))
                        // R2 ignores the region; "auto" is its convention.
                        .region(Region.of("auto"))
                        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                        .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                        .httpClient(UrlConnectionHttpClient.create())
                        .build()
                : null;

        if (!complete) {
            log.warn("Media storage is not configured: photo and video uploads are disabled (links still work).");
        }
    }

    @Override
    public boolean isConfigured() {
        return client != null;
    }

    @Override
    public void put(String key, Content content, long size, String contentType) {
        ContentStreamProvider provider = () -> {
            try {
                return content.open();
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        };
        client.putObject(
                b -> b.bucket(bucket).key(key).contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable"),
                RequestBody.fromContentProvider(provider, size, contentType));
    }

    @Override
    public void delete(String key) {
        client.deleteObject(b -> b.bucket(bucket).key(key));
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }
}
