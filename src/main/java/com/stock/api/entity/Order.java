package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant une commande.
 * Gestion des statuts avec transitions contrôlées.
 * Calcul automatique du montant total.
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    /** Entreprise propriétaire (V2 multi-entreprises). */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * Statut de la commande avec transitions contrôlées.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private OrderStatus status = OrderStatus.PENDING;

    /**
     * Montant total calculé automatiquement.
     */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    /**
     * Lignes de commande.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderLine> lines = new ArrayList<>();

    @Column(length = 500)
    private String notes;

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
     * Calcule le montant total à partir des lignes de commande.
     */
    public void calculateTotal() {
        this.totalAmount = lines.stream()
                .map(OrderLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Ajoute une ligne et recalcule le total.
     */
    public void addLine(OrderLine line) {
        lines.add(line);
        line.setOrder(this);
        calculateTotal();
    }

    /**
     * Vérifie si la transition de statut est autorisée.
     */
    public boolean canTransitionTo(OrderStatus newStatus) {
        return switch (this.status) {
            case PENDING -> newStatus == OrderStatus.VALIDATED || newStatus == OrderStatus.CANCELLED;
            case VALIDATED -> newStatus == OrderStatus.CANCELLED; // Annulation avec restitution stock
            case CANCELLED -> false; // Pas de transition depuis CANCELLED
        };
    }

    /**
     * Applique une transition de statut si elle est valide.
     */
    public void transitionTo(OrderStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    String.format("Transition de %s vers %s non autorisée", this.status, newStatus));
        }
        this.status = newStatus;
    }

    public enum OrderStatus {
        PENDING("En attente"),
        VALIDATED("Validée"),
        CANCELLED("Annulée");

        private final String description;

        OrderStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
