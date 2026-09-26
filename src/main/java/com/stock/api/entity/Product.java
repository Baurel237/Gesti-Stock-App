package com.stock.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entité représentant un produit dans le stock.
 * RG-01 : quantité jamais négative.
 * RG-04 : suppression logique des produits.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unicité du nom portée par un index partiel (lignes actives uniquement,
     * RG-04) créé au démarrage par DataInitializer. Une contrainte UNIQUE
     * globale bloquerait la recréation d'un produit au nom d'un produit
     * soft-deleted.
     */
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 50)
    private String reference;

    /** Chemin d'image unique généré par le stockage applicatif. */
    @Column(name = "image_path", length = 255)
    private String imagePath;

    /** Type MIME vérifié à l'envoi (PNG ou JPEG). */
    @Column(name = "image_content_type", length = 50)
    private String imageContentType;

    /**
     * RG-01 : quantité en stock, jamais négative.
     */
    @Min(value = 0, message = "La quantité ne peut pas être négative")
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /**
     * Seuil d'alerte pour le stock bas.
     */
    @Min(value = 0, message = "Le seuil d'alerte ne peut pas être négatif")
    @Column(nullable = false)
    @Builder.Default
    private Integer alertThreshold = 10;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** Entrepropriétaire (V2 multi-entreprises) — isolation des données. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * RG-04 : suppression logique (soft delete).
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * RG-01 : vérifie qu'on peut retirer la quantité demandée.
     */
    public boolean canRemoveQuantity(int quantityToRemove) {
        return this.quantity >= quantityToRemove;
    }

    /**
     * RG-01 : ajoute de la quantité (jamais en dessous de 0).
     */
    public void addQuantity(int quantityToAdd) {
        this.quantity = Math.max(0, this.quantity + quantityToAdd);
    }

    /**
     * RG-01 : retire de la quantité (jamais en dessous de 0).
     */
    public void removeQuantity(int quantityToRemove) {
        this.quantity = Math.max(0, this.quantity - quantityToRemove);
    }
}
