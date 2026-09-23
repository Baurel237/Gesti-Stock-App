package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant une vente réalisée par un vendeur.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    /** Entreprise propriétaire (V2 multi-entreprises) — isolation des données. */
    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /**
     * Entrepôt de décrémentation (module optionnel V2) — null en mode stock simple.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleStatus status = SaleStatus.COMPLETED;

    /**
     * Mode de paiement choisi au moment de la vente (affiché sur le reçu).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(nullable = false)
    @Builder.Default
    private Integer totalItems = 0;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleItem> items = new ArrayList<>();

    /**
     * Acheteur optionnel — renseigné pour les ventes de montants élevés
     * (facultatif, affiché sur le reçu).
     */
    @Column(length = 100)
    private String buyerName;

    @Column(length = 30)
    private String buyerPhone;

    @Column(length = 500)
    private String notes;

    /**
     * Demande d'annulation en attente émise par le vendeur après le délai
     * d'auto-annulation (4 h) — à traiter par l'administration.
     * Repassée à false dès que la vente est annulée.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean cancellationRequested = false;

    /** Motif fourni par le vendeur lors de la demande d'annulation. */
    @Column(length = 500)
    private String cancellationReason;

    /** Date de la demande d'annulation. */
    @Column
    private LocalDateTime cancellationRequestedAt;

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

    public void calculateTotal() {
        this.totalAmount = items.stream()
                .map(SaleItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        this.totalItems = items.stream()
                .mapToInt(SaleItem::getQuantity)
                .sum();
    }

    public void addItem(SaleItem item) {
        items.add(item);
        item.setSale(this);
        calculateTotal();
    }

    public enum PaymentMethod {
        CASH("Espèces"),
        CARD("Carte"),
        MOBILE("Mobile");

        private final String description;

        PaymentMethod(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public enum SaleStatus {
        COMPLETED("Effectuée"),
        CANCELLED("Annulée");

        private final String description;

        SaleStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
