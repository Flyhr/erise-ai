package com.erise.ai.backend.integration.storage;

import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class MinioStorageClient {

    private final EriseProperties properties;
    private final MinioClient minioClient;

    public MinioStorageClient(EriseProperties properties) {
        this.properties = properties;
        this.minioClient = MinioClient.builder()
                .endpoint(properties.getStorage().getEndpoint())
                .credentials(properties.getStorage().getAccessKey(), properties.getStorage().getSecretKey())
                .build();
        ensureBucketExists();
    }

    public void putObject(String objectKey, MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            putObject(objectKey, inputStream, file.getSize(), file.getContentType());
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to upload file: " + exception.getMessage());
        }
    }

    public void putObject(String objectKey, byte[] bytes, String contentType) {
        try (InputStream inputStream = new ByteArrayInputStream(bytes == null ? new byte[0] : bytes)) {
            putObject(objectKey, inputStream, bytes == null ? 0 : bytes.length, contentType);
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to upload file: " + exception.getMessage());
        }
    }

    public InputStream getObject(String objectKey) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(properties.getStorage().getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to read file: " + exception.getMessage());
        }
    }

    public boolean objectExists(String objectKey) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(properties.getStorage().getBucket())
                            .object(objectKey)
                            .build()
            );
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public void removeObject(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getStorage().getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ignored) {
            // Cleanup is best-effort.
        }
    }

    public void moveObject(String sourceKey, String targetKey) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(properties.getStorage().getBucket())
                            .object(targetKey)
                            .source(CopySource.builder()
                                    .bucket(properties.getStorage().getBucket())
                                    .object(sourceKey)
                                    .build())
                            .build()
            );
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getStorage().getBucket())
                            .object(sourceKey)
                            .build()
            );
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to move file: " + exception.getMessage());
        }
    }

    public String bucket() {
        return properties.getStorage().getBucket();
    }

    private void putObject(String objectKey, InputStream inputStream, long size, String contentType) throws Exception {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(properties.getStorage().getBucket())
                        .object(objectKey)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    }

    private void ensureBucketExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.getStorage().getBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getStorage().getBucket()).build());
            }
        } catch (Exception exception) {
            throw new BizException(ErrorCodes.FILE_ERROR, "Failed to initialize object storage: " + exception.getMessage());
        }
    }
}
