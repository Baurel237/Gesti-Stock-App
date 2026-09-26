package com.stock.api.service;

import com.stock.api.entity.Product;
import com.stock.api.repository.ProductRepository;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductRepository productRepository;
    private final ProductImageStorageService storage;

    @Transactional
    public Product upload(Long productId, org.springframework.web.multipart.MultipartFile file) {
        Product product = findAccessibleProduct(productId);
        ProductImageStorageService.StoredImage stored = storage.store(file);
        String previousImage = product.getImagePath();
        try {
            product.setImagePath(stored.filename());
            product.setImageContentType(stored.contentType());
            productRepository.save(product);
            afterCommit(() -> storage.delete(previousImage));
            afterRollback(() -> storage.delete(stored.filename()));
            return product;
        } catch (RuntimeException exception) {
            storage.delete(stored.filename());
            throw exception;
        }
    }

    @Transactional
    public void remove(Long productId) {
        Product product = findAccessibleProduct(productId);
        String previousImage = product.getImagePath();
        product.setImagePath(null);
        product.setImageContentType(null);
        productRepository.save(product);
        afterCommit(() -> storage.delete(previousImage));
    }

    @Transactional(readOnly = true)
    public ProductImageFile load(Long productId) {
        Product product = findAccessibleProduct(productId);
        if (product.getImagePath() == null || product.getImageContentType() == null) {
            throw new IllegalArgumentException("Ce produit n'a pas de photo.");
        }
        Path path = storage.resolveStoredPath(product.getImagePath());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("La photo du produit est introuvable.");
        }
        return new ProductImageFile(path, product.getImageContentType());
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static void afterRollback(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) action.run();
            }
        });
    }

    private Product findAccessibleProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé avec l'id: " + productId));
        if (product.isDeleted()) {
            throw new IllegalArgumentException("Produit supprimé");
        }
        TenantGuard.assertSameCompany(product.getCompanyId());
        return product;
    }

    public record ProductImageFile(Path path, String contentType) {}
}
