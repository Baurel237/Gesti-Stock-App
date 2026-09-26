package com.stock.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class ProductImageStorageService {

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private final Path storageDirectory;

    public ProductImageStorageService(
            @Value("${stock.product-image.storage-dir:uploads/product-images}") String storageDirectory) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidProductImageException("Sélectionnez une image à envoyer.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new InvalidProductImageException("L'image ne doit pas dépasser 5 Mo.");
        }

        try {
            byte[] header = readHeader(file);
            String contentType;
            String extension;
            if (isPng(header)) {
                contentType = "image/png";
                extension = ".png";
            } else if (isJpeg(header)) {
                contentType = "image/jpeg";
                extension = ".jpg";
            } else {
                throw new InvalidProductImageException("Seules les images PNG et JPEG sont acceptées.");
            }

            Files.createDirectories(storageDirectory);
            String filename = UUID.randomUUID() + extension;
            Path destination = resolveStoredPath(filename);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination);
            }
            return new StoredImage(filename, contentType);
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible d'enregistrer l'image du produit.", exception);
        }
    }

    public Path resolveStoredPath(String filename) {
        if (filename == null || filename.isBlank() || filename.contains("/") || filename.contains("\\")
                || !Path.of(filename).getFileName().toString().equals(filename)) {
            throw new IllegalArgumentException("Chemin d'image invalide.");
        }
        Path resolved = storageDirectory.resolve(filename).normalize();
        if (!resolved.startsWith(storageDirectory)) {
            throw new IllegalArgumentException("Chemin d'image invalide.");
        }
        return resolved;
    }

    public void delete(String filename) {
        if (filename == null || filename.isBlank()) return;
        try {
            Files.deleteIfExists(resolveStoredPath(filename));
        } catch (IOException exception) {
            throw new IllegalStateException("Impossible de supprimer l'image du produit.", exception);
        }
    }

    private static byte[] readHeader(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return input.readNBytes(8);
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

    public record StoredImage(String filename, String contentType) {}
}
