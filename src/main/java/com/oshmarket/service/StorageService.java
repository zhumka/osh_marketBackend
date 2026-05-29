package com.oshmarket.service;

import com.oshmarket.config.MinioProperties;
import com.oshmarket.dto.FileContent;
import com.oshmarket.exception.ApiException;
import io.minio.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Обёртка над MinIO: гарантирует существование бакета, загружает и отдаёт объекты.
 * Ключи объектов (а не сами файлы) хранятся в БД.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MinioClient minioClient;
    private final MinioProperties props;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(props.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
                log.info("MinIO bucket '{}' создан", props.getBucket());
            }
        } catch (Exception e) {
            log.warn("Не удалось проверить/создать бакет MinIO '{}': {}", props.getBucket(), e.getMessage());
        }
    }

    /**
     * Загружает файл в хранилище под ключом вида {prefix}/{uuid}.{ext} и возвращает ключ.
     */
    public String upload(String prefix, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Файл не передан или пуст");
        }
        String key = buildKey(prefix, file.getOriginalFilename());
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(key)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                    .build());
            return key;
        } catch (Exception e) {
            log.error("Ошибка загрузки файла в MinIO (key={})", key, e);
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                    "Не удалось сохранить файл");
        }
    }

    /**
     * Считывает объект из хранилища целиком.
     */
    public FileContent get(String key, String contentType) {
        if (key == null) {
            throw ApiException.notFound("Файл не найден");
        }
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(props.getBucket())
                .object(key)
                .build())) {
            byte[] bytes = response.readAllBytes();
            String filename = key.substring(key.lastIndexOf('/') + 1);
            String type = contentType != null ? contentType : "application/octet-stream";
            return new FileContent(bytes, type, filename);
        } catch (Exception e) {
            log.error("Ошибка чтения файла из MinIO (key={})", key, e);
            throw ApiException.notFound("Файл не найден в хранилище");
        }
    }

    public void remove(String key) {
        if (key == null) return;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(key)
                    .build());
        } catch (Exception e) {
            log.warn("Не удалось удалить объект MinIO (key={}): {}", key, e.getMessage());
        }
    }

    private String buildKey(String prefix, String originalFilename) {
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        }
        return prefix + "/" + UUID.randomUUID() + ext;
    }
}
