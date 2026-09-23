package com.stock.api.service;

import com.stock.api.dto.CategoryRequest;
import com.stock.api.dto.CategoryResponse;
import com.stock.api.entity.Category;
import com.stock.api.repository.CategoryRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.tenant.TenantContext;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service de gestion des catégories (US-04).
 * RG-04 : suppression logique des catégories.
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    /**
     * US-04 : Liste paginée des catégories actives.
     */
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(Pageable pageable) {
        return categoryRepository.findByFilters(TenantContext.getCompanyId(), pageable)
                .map(this::toResponse);
    }

    /**
     * US-04 : Détail d'une catégorie par ID.
     */
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie non trouvée avec l'id: " + id));
        TenantGuard.assertSameCompany(category.getCompanyId());
        if (category.isDeleted()) {
            throw new IllegalArgumentException("Catégorie supprimée");
        }
        return toResponse(category);
    }

    /**
     * US-04 : Création d'une catégorie.
     */
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        Long companyId = TenantGuard.requireCompanyId();
        if (categoryRepository.existsByCompanyIdAndNameAndDeletedFalse(companyId, request.getName())) {
            throw new IllegalStateException("Une catégorie avec ce nom existe déjà");
        }

        Category category = Category.builder()
                .name(request.getName())
                .description(request.getDescription())
                .companyId(companyId)
                .build();

        category = categoryRepository.save(category);
        return toResponse(category);
    }

    /**
     * US-04 : Mise à jour d'une catégorie.
     */
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie non trouvée avec l'id: " + id));

        if (category.isDeleted()) {
            throw new IllegalArgumentException("Catégorie supprimée");
        }
        TenantGuard.assertSameCompany(category.getCompanyId());

        // Vérifier l'unicité du nom si modifié
        if (!category.getName().equals(request.getName())
                && categoryRepository.existsByCompanyIdAndNameAndDeletedFalse(
                        TenantContext.getCompanyId(), request.getName())) {
            throw new IllegalStateException("Une catégorie avec ce nom existe déjà");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());

        category = categoryRepository.save(category);
        return toResponse(category);
    }

    /**
     * US-04 : Suppression logique (RG-04).
     * Interdite si des produits sont rattachés.
     */
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Catégorie non trouvée avec l'id: " + id));

        if (category.isDeleted()) {
            throw new IllegalArgumentException("Catégorie déjà supprimée");
        }
        TenantGuard.assertSameCompany(category.getCompanyId());

        // Vérifier qu'aucun produit actif n'est rattaché
        if (!productRepository.findByCategory_IdAndDeletedFalse(id, Pageable.unpaged()).isEmpty()) {
            throw new IllegalStateException(
                    "Impossible de supprimer cette catégorie : des produits y sont encore rattachés");
        }

        category.setDeleted(true);
        categoryRepository.save(category);
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .description(category.getDescription())
                .deleted(category.isDeleted())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
