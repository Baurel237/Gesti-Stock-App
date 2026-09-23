package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stock d'un produit DANS un entrepôt (module optionnel).
 * La quantité totale du produit reste Product.quantity (source de vérité
 * du stock global) ; cette table décompose le stock par entrepôt.
 */
@Entity
@Table(name = "product_stock_per_warehouse",
       uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "warehouse_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    /** Quantité présente dans cet entrepôt (jamais négative). */
    @Column(nullable = false)
    @Builder.Default
    private Integer quantity = 0;

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

    public boolean canRemove(int qty) {
        return this.quantity >= qty;
    }
}
