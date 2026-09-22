package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Demande de modification d'une vente émise par le vendeur après le délai
 * d'auto-modification (4 h). Contient les nouveaux articles et informations
 * souhaités. Traitée (approuvée / refusée) par l'administration.
 */
@Entity
@Table(name = "sale_edit_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleEditRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SaleEditRequestStatus status = SaleEditRequestStatus.PENDING;

    /** Motif fourni par le vendeur lors de la demande de modification. */
    @Column(length = 500)
    private String reason;

    /** Nouveau mode de paiement proposé. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sale.PaymentMethod paymentMethod;

    /** Nouvel acheteur proposé (facultatif). */
    @Column(length = 100)
    private String buyerName;

    @Column(length = 30)
    private String buyerPhone;

    /** Nouvelles notes proposées. */
    @Column(length = 500)
    private String notes;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SaleEditRequestItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    @Column
    private LocalDateTime reviewedAt;

    public void addItem(SaleEditRequestItem item) {
        items.add(item);
        item.setRequest(this);
    }

    public enum SaleEditRequestStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}