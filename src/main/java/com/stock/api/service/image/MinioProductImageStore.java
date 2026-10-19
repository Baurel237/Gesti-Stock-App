package com.stock.api.service.image;

import com.stock.api.service.InvalidProductImageException;
import com.stock.api.service.ProductImageStorageService.StoredImage;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

/**
 * Stockage des images produits sur MinIO (ou tout endpoint compatible S3).
 *
 * Activé avec {@code stock.image-storage.mode=s3}. Le bucket est créé au
 * démarrage s'il est absent. Les objets sont lus intégralement en mémoire
 * (plafond 5 Mo) pour rester compatibles avec le resource loader du contrôleur.
 */
@Service
@ConditionalOnProperty(name = "stock.image-storage.mode", havingValue = "s3")
public class MinioProductImageStore implements ProductImageStore {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String PNG_SIGNATURE = "\u0089PNG\r\n\u001A\n";
    private static final String PNG_EXTENSION = ".png";
    private static final String JPG_EXTENSION = ".jpg";
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_JPEG = "image/jpeg";

    private final MinioClient minioClient;
    private final String bucket;

    public MinioProductImageStore(
            @Value("${stock.image-storage.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${stock.image-storage.minio.access-key:minioadmin}") String accessKey,
            @Value("${stock.image-storage.minio.secret-key:minioadmin}") String secretKey,
            @Value("${stock.image-storage.minio.bucket:product-images}") String bucket) {
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        this.bucket = bucket;
        ensureBucketExists();
    }

    @Override
    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProductImageException("Sélectionnez une image à envoyer.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new InvalidProductImageException("L'image ne doit pas dépasser 5 Mo.");
        }

        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(8);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible d'analyser l'image du produit.", exception);
        }

        String extension;
        String contentType;
        if (isPng(header)) {
            extension = PNG_EXTENSION;
            contentType = CONTENT_TYPE_PNG;
        } else if (isJpeg(header)) {
            extension = JPG_EXTENSION;
            contentType = CONTENT_TYPE_JPEG;
        } else {
            throw new InvalidProductImageException("Seules les images PNG et JPEG sont acceptées.");
        }

        String key = UUID.randomUUID() + extension;
        try (InputStream input = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return new StoredImage(key, contentType);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible d'enregistrer l'image du produit.", exception);
        }
    }

    @Override
    public Resource read(String key, String contentType) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Chemin d'image invalide.");
        }
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(key)
                .build())) {
            byte[] bytes = response.readAllBytes();
            return new ByteArrayResource(bytes);
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())) {
                throw new IllegalArgumentException("La photo du produit est introuvable.");
            }
            throw new IllegalStateException("Impossible de lire l'image du produit.", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de lire l'image du produit.", exception);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (ErrorResponseException exception) {
            // Objet déjà absent : suppression idempotente.
        } catch (Exception exception) {
            throw new IllegalStateException("Impossible de supprimer l'image du produit.", exception);
        }
    }

    private void ensureBucketExists() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Impossible de préparer le bucket MinIO « " + bucket + " ».", exception);
        }
    }

    private static boolean isPng(byte[] header) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (header.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if (header[i] != signature[i]) return false;
        }
        return true;
    }

    private static boolean isJpeg(byte[] header) {
        return header.length >= 3 && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
    }
}
