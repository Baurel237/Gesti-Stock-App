package com.stock.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Commentaire laissé par un vendeur sur un produit pour alerter
 * l'administration (ex : « stock presque épuisé, les clients le
 * réclament »). Réservé à la lecture des rôles de gestion.
 */
@Entity
@Table(name = "product_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Vendeur (ou autre utilisateur) auteur du commentaire. */
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    /** Entreprise propriétaire (V2 multi-entreprises). */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Contenu du commentaire (ex : « Presque fini, les clients réclament »). */
    @NotBlank
    @Column(nullable = false, length = 500)
    private String content;

    /**
     * Catégorie d'alerte choisie par le vendeur :
     * LOW_STOCK, CUSTOMER_REQUEST ou OTHER.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CommentCategory category = CommentCategory.LOW_STOCK;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum CommentCategory {
        LOW_STOCK("Stock presque épuisé"),
        CUSTOMER_REQUEST("Réclamé par les clients"),
        OTHER("Autre");

        private final String description;

        CommentCategory(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
