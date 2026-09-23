package com.stock.api.service;

import com.stock.api.dto.ProductCommentRequest;
import com.stock.api.dto.ProductCommentResponse;
import com.stock.api.entity.Product;
import com.stock.api.entity.ProductComment;
import com.stock.api.entity.User;
import com.stock.api.exception.BusinessRuleException;
import com.stock.api.repository.ProductCommentRepository;
import com.stock.api.repository.ProductRepository;
import com.stock.api.repository.UserRepository;
import com.stock.api.tenant.TenantGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Commentaires des vendeurs sur les produits : canal d'alerte vers
 * l'administration (« stock presque épuisé », « clients réclamant »...).
 */
@Service
@RequiredArgsConstructor
public class ProductCommentService {

    private final ProductCommentRepository productCommentRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Un vendeur (ou tout utilisateur authentifié) signale quelque chose
     * sur un produit ; l'action est tracée dans le journal d'audit pour
     * que l'admin la retrouve.
     */
    @Transactional
    public ProductCommentResponse create(Long productId, ProductCommentRequest request, String authorEmail) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé avec l'id: " + productId));
        if (product.isDeleted()) {
            throw new IllegalArgumentException("Produit supprimé");
        }

        User author = userRepository.findByEmail(authorEmail)
                .orElseThrow(() -> new BusinessRuleException("Utilisateur non trouvé"));
        TenantGuard.assertSameCompany(product.getCompanyId());

        ProductComment comment = ProductComment.builder()
                .product(product)
                .author(author)
                .companyId(product.getCompanyId())
                .content(request.getContent().trim())
                .category(request.getCategory())
                .build();

        ProductComment saved = productCommentRepository.save(comment);

        auditService.record("COMMENT", "Product", product.getId(), product.getName(),
                String.format("%s : « %s »", request.getCategory(), saved.getContent()));

        return toResponse(saved);
    }

    /** Derniers commentaires d'un produit — affichés sur la carte produit du vendeur. */
    @Transactional(readOnly = true)
    public List<ProductCommentResponse> findRecentByProduct(Long productId) {
        return productCommentRepository.findTop5ByProductIdOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Journal complet des commentaires pour l'administration. */
    @Transactional(readOnly = true)
    public Page<ProductCommentResponse> findAll(Pageable pageable) {
        return productCommentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    private ProductCommentResponse toResponse(ProductComment comment) {
        return ProductCommentResponse.builder()
                .id(comment.getId())
                .productId(comment.getProduct().getId())
                .productName(comment.getProduct().getName())
                .productReference(comment.getProduct().getReference())
                .authorId(comment.getAuthor().getId())
                .authorName(comment.getAuthor().getFirstName() + " " + comment.getAuthor().getLastName())
                .content(comment.getContent())
                .category(comment.getCategory())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
