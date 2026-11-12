package com.fajing.service.storage;

import com.fajing.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
import java.util.UUID;

/**
 * S3 兼容对象存储（阿里云 OSS / 腾讯云 COS / MinIO 均可）
 * 通过 storage.type=s3 启用，配置缺失时在调用处给出明确错误而不是抛堆栈给前端。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    @Value("${storage.s3.endpoint:}")
    private String endpoint;
    @Value("${storage.s3.bucket:}")
    private String bucket;
    @Value("${storage.s3.access-key:}")
    private String accessKey;
    @Value("${storage.s3.secret-key:}")
    private String secretKey;
    @Value("${storage.s3.region:auto}")
    private String region;
    @Value("${storage.s3.public-base-url:}")
    private String publicBaseUrl;
    @Value("${storage.s3.prefix:fajing}")
    private String prefix;

    private S3Client client;

    private synchronized S3Client client() {
        if (client != null) {
            return client;
        }
        if (isBlank(endpoint) || isBlank(bucket) || isBlank(accessKey) || isBlank(secretKey)) {
            throw new BizException("对象存储配置不完整，请检查 S3_ENDPOINT / S3_BUCKET / S3_ACCESS_KEY / S3_SECRET_KEY");
        }
        S3ClientBuilder builder = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(isBlank(region) ? "auto" : region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true);
        client = builder.build();
        return client;
    }

    @Override
    public String put(byte[] bytes, String ext, String prefixName) {
        String safeExt = isBlank(ext) ? "jpg" : ext.toLowerCase();
        String name = (isBlank(prefixName) ? UUID.randomUUID().toString().replace("-", "") : prefixName)
                + "." + safeExt;
        String key = trimSlash(prefix) + "/" + name;
        try {
            client().putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType(safeExt))
                            .build(),
                    RequestBody.fromBytes(bytes));
            return publicUrl(key);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("对象存储上传失败", e);
            throw new BizException("图片上传到对象存储失败");
        }
    }

    @Override
    public void delete(String url) {
        String key = keyOf(url);
        if (key.isBlank()) {
            return;
        }
        try {
            client().deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception e) {
            log.warn("对象存储删除失败：{}", e.getMessage());
        }
    }

    private String publicUrl(String key) {
        String base = isBlank(publicBaseUrl) ? trimSlash(endpoint) + "/" + bucket : trimSlash(publicBaseUrl);
        return base + "/" + key;
    }

    private String keyOf(String url) {
        if (isBlank(url)) {
            return "";
        }
        int q = url.indexOf('?');
        String clean = q > -1 ? url.substring(0, q) : url;
        String base = isBlank(publicBaseUrl) ? trimSlash(endpoint) + "/" + bucket : trimSlash(publicBaseUrl);
        if (clean.startsWith(base)) {
            return clean.substring(base.length()).replaceFirst("^/", "");
        }
        int idx = clean.indexOf("/" + trimSlash(prefix) + "/");
        return idx >= 0 ? clean.substring(idx + 1) : "";
    }

    private String contentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String trimSlash(String s) {
        if (s == null) {
            return "";
        }
        String v = s.trim();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
