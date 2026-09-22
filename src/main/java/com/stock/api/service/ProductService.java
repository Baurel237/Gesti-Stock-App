package com.stock.api.service;

import com.stock.api.dto.ProductRequest;
import com.stock.api.dto.ProductResponse;
import com.stock.api.entity.Category;
import com.stock.api.entity.Product;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des produits (US-05, US-06).
 * RG-01 : quantité jamais négative.
 * RG-04 : suppression logique.
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    /**
     * US-06 : Liste paginée des produits actifs avec filtres.
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> findAll(Long categoryId, String name, Pageable pageable) {
        return productRepository.findByFilters(categoryId, name, pageable)
                .map(this::toResponse);
    }

    /**
     * US-05 : Détail d'un produit par ID.
     */
    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé avec l'id: " + id));
        if (product.isDeleted()) {
            throw new IllegalArgumentException("Produit supprimé");
        }
        return toResponse(product);
    }

    /**
     * US-05 : Liste des produits en stock bas (US-06).
     */
    @Transactional(readOnly = true)
    public Page<ProductResponse> findLowStock(Pageable pageable) {
        return productRepository.findLowStockProducts(pageable)
                .map(this::toResponse);
    }

    /**
     * US-05 : Création d'un produit rattaché à une catégorie.
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        // Vérifier l'unicité du nom
        if (productRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new IllegalStateException("Un produit avec ce nom existe déjà");
        }

        // Vérifier l'unicité de la référence
        if (productRepository.existsByReferenceAndDeletedFalse(request.getReference())) {
            throw new IllegalStateException("Un produit avec cette référence existe déjà");
        }

        // Vérifier que la catégorie existe et n'est pas supprimée
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Catégorie non trouvée avec l'id: " + request.getCategoryId()));
        if (category.isDeleted()) {
            throw new IllegalArgumentException("Catégorie supprimée");
        }

        Product product = Product.builder()
                .name(request.getName())
                .description(request.getDescription())
                .reference(request.getReference())
                .price(request.getPrice())
                .quantity(request.getQuantity() != null ? request.getQuantity() : 0)
                .alertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : 10)
                .category(category)
                .build();

        product = productRepository.save(product);
        return toResponse(product);
    }

    /**
     * US-05 : Mise à jour d'un produit.
     */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé avec l'id: " + id));

        if (product.isDeleted()) {
            throw new IllegalArgumentException("Produit supprimé");
        }

        // Vérifier l'unicité du nom si modifié
        if (!product.getName().equals(request.getName())
                && productRepository.existsByNameAndDeletedFalse(request.getName())) {
            throw new IllegalStateException("Un produit avec ce nom existe déjà");
        }

        // Vérifier l'unicité de la référence si modifiée
        if (!product.getReference().equals(request.getReference())
                && productRepository.existsByReferenceAndDeletedFalse(request.getReference())) {
            throw new IllegalStateException("Un produit avec cette référence existe déjà");
        }

        // Vérifier la catégorie
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new IllegalArgumentException("Catégorie non trouvée avec l'id: " + request.getCategoryId()));
        if (category.isDeleted()) {
            throw new IllegalArgumentException("Catégorie supprimée");
        }

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setReference(request.getReference());
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity() != null ? request.getQuantity() : product.getQuantity());
        product.setAlertThreshold(request.getAlertThreshold() != null ? request.getAlertThreshold() : product.getAlertThreshold());
        product.setCategory(category);

        product = productRepository.save(product);
        return toResponse(product);
    }

    /**
     * US-05 : Suppression logique (RG-04).
     */
    @Transactional
    public void delete(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé avec l'id: " + id));

        if (product.isDeleted()) {
            throw new IllegalArgumentException("Produit déjà supprimé");
        }

        product.setDeleted(true);
        productRepository.save(product);
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .reference(product.getReference())
                .price(product.getPrice())
                .quantity(product.getQuantity())
                .alertThreshold(product.getAlertThreshold())
                .lowStock(product.getQuantity() <= product.getAlertThreshold())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .deleted(product.isDeleted())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
