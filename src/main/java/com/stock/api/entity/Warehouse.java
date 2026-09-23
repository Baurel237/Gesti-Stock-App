package com.stock.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entrepôt d'une entreprise — existe uniquement pour les entreprises ayant
 * activé le module optionnel (Company.warehouseEnabled = true).
 * Le stock est décomposé par entrepôt via product_stock_per_warehouse ;
 * un mouvement sans entrepôt (warehouse = null) reste valable en mode stock simple.
 */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @NotBlank
    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String location;

    /** Désactivé = conservé pour l'historique mais exclu des opérations. */
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

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
