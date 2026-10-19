package com.stock.api.service.image;

import com.stock.api.service.ProductImageStorageService.StoredImage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Backend de stockage d'images sur le disque local (mode par défaut).
 * Délègue toute la logique (validation, chemins, suppression) au service
 * historique {@link com.stock.api.service.ProductImageStorageService}.
 */
@Service
@ConditionalOnProperty(
        name = "stock.image-storage.mode",
        havingValue = "local",
        matchIfMissing = true)
public class LocalStorageProductImageStore implements ProductImageStore {

    private final com.stock.api.service.ProductImageStorageService storage;

    public LocalStorageProductImageStore(com.stock.api.service.ProductImageStorageService storage) {
        this.storage = storage;
    }

    @Override
    public StoredImage store(MultipartFile file) {
        return storage.store(file);
    }

    @Override
    public Resource read(String key, String contentType) {
        Path path = storage.resolveStoredPath(key);
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("La photo du produit est introuvable.");
        }
        return new FileSystemResource(path);
    }

    @Override
    public void delete(String key) {
        storage.delete(key);
    }
}
