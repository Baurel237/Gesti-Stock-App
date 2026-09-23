package com.stock.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entreprise (tenant) de la plateforme.
 * Toutes les données métier (utilisateurs hors plateforme, produits, ventes...)
 * sont rattachées à une entreprise ; le SUPER_ADMIN est global (sans entreprise).
 */
@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    /** Identifiant URL unique (ex : "demo", "boutique-abidjan"). */
    @NotBlank
    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    /** Suspendue = false : les utilisateurs de l'entreprise ne peuvent plus accéder. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * Module entrepôts OPTIONNEL. false (défaut) = stock simple existant
     * (Product.quantity, aucun entrepôt requis). true = l'entreprise peut
     * créer des entrepôts et gérer le stock par entrepôt.
     */
    @Column(name = "warehouse_enabled", nullable = false)
    @Builder.Default
    private boolean warehouseEnabled = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
