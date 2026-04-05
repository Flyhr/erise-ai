package com.erise.ai.backend.integration.storage;

import com.erise.ai.backend.common.config.EriseProperties;
import com.erise.ai.backend.common.exception.BizException;
import com.erise.ai.backend.common.exception.ErrorCodes;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
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
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getStorage().getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
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

    public String bucket() {
        return properties.getStorage().getBucket();
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
