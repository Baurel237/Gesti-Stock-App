package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Fournisseur d'une entreprise (story 2.1).
 * RG-04 : suppression logique (soft delete), comme les produits et catégories.
 * L'unicité du nom est portée par un index partiel par entreprise (lignes
 * non supprimées uniquement) — voir Category.name.
 */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 100)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String address;

    /** Entreprise propriétaire (V2 multi-entreprises). */
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
}
