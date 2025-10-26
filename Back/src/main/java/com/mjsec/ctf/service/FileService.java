package com.mjsec.ctf.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.mjsec.ctf.util.PathTraversalValidator;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.ResourceUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.io.FileNotFoundException;

@Slf4j
@Service
public class FileService {

    @Value("${spring.cloud.gcp.storage.credentials.location}")
    private String keyFileName;

    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    public String store(MultipartFile multipartFile) throws IOException {
        InputStream keyFile = ResourceUtils.getURL(keyFileName).openStream();

        String uuid = UUID.randomUUID().toString();
        String ext = multipartFile.getContentType();

        Storage storage = StorageOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(keyFile))
                .build()
                .getService();

        String imgUrl = "https://storage.googleapis.com/" + bucketName + "/" + uuid;

        if (multipartFile.isEmpty()) {
            imgUrl = null;
        } else {
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, uuid)
                    .setContentType(ext).build();

            Blob blob = storage.create(blobInfo, multipartFile.getInputStream());
        }

        return imgUrl;
    }
    /**
     * 파일 다운로드 기능 (Path Traversal 방어 적용)
     */
    public byte[] download(String fileId) throws IOException {
        // ========================================
        // 1. Path Traversal 공격 방어
        // ========================================

        // fileId가 null이거나 비어있는 경우
        if (fileId == null || fileId.trim().isEmpty()) {
            log.error("File download failed: fileId is null or empty");
            throw new IllegalArgumentException("File ID cannot be null or empty");
        }

        // Path Traversal 패턴 검증
        if (!PathTraversalValidator.isValidFilename(fileId)) {
            log.error("🚨 Path Traversal Attack Blocked: fileId = {}", fileId);
            throw new SecurityException("Invalid file ID: Path traversal detected");
        }

        // UUID 형식 검증 (GCP Storage는 UUID 사용)
        if (!PathTraversalValidator.isValidUUID(fileId)) {
            log.error("🚨 Invalid UUID Format: fileId = {}", fileId);
            throw new IllegalArgumentException("Invalid file ID format: Must be UUID");
        }

        log.info("File download requested: fileId = {}", fileId);

        // ========================================
        // 2. GCP Storage에서 파일 다운로드
        // ========================================

        InputStream keyFile = ResourceUtils.getURL(keyFileName).openStream();
        Storage storage = StorageOptions.newBuilder()
                .setCredentials(GoogleCredentials.fromStream(keyFile))
                .build()
                .getService();

        Blob blob = storage.get(bucketName, fileId);
        if (blob == null) {
            log.error("File not found in GCP Storage: fileId = {}", fileId);
            throw new FileNotFoundException("File not found with id: " + fileId);
        }

        log.info("File download successful: fileId = {}, size = {} bytes", fileId, blob.getSize());

        // 파일의 내용을 byte 배열로 반환
        return blob.getContent();
    }
}
