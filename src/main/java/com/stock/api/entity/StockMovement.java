package com.stock.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entité représentant un mouvement de stock (entrée/sortie).
 * Traçabilité complète : auteur, date, type, quantité.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Le type de mouvement est obligatoire")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MovementType type;

    @NotNull(message = "Le produit est obligatoire")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Quantité du mouvement (toujours positive).
     */
    @Min(value = 1, message = "La quantité doit être au moins 1")
    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 500)
    private String reason;

    /**
     * Référence à la commande liée (si mouvement déclenché par une commande).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /**
     * Auteur du mouvement (traçabilité).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by_id", nullable = false)
    private User performedBy;

    /** Entreprise propriétaire (V2 multi-entreprises). */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * Entrepôt concerné (module optionnel V2) — null en mode stock simple.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public enum MovementType {
        ENTRY("Entrée"),
        EXIT("Sortie");

        private final String description;

        MovementType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
