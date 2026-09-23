package com.stock.api.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Journal d'audit — traçabilité de toutes les actions métier.
 * Chaque création/modification/suppression (produits, catégories,
 * mouvements de stock, commandes, ventes, utilisateurs) est enregistrée
 * avec l'auteur, la date et les détails.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Entreprise concernée (null = action plateforme / système). */
    @Column(name = "company_id")
    private Long companyId;

    /** Email de l'utilisateur qui a effectué l'action. */
    @Column(nullable = false, length = 100)
    private String actorEmail;

    /** Ex : "Vendeur" — description du rôle le plus élevé au moment de l'action. */
    @Column(length = 50)
    private String actorRoles;

    /** Type d'action : CREATE, UPDATE, DELETE, EXIT, ENTRY, VALIDATE, CANCEL. */
    @Column(nullable = false, length = 30)
    private String action;

    /** Type d'entité : Product, Category, StockMovement, Order, Sale, User... */
    @Column(nullable = false, length = 50)
    private String entityType;

    /** Identifiant de l'entité concernée (null si non applicable). */
    private Long entityId;

    /** Nom/description lisible de l'entité (ex : nom du produit). */
    @Column(length = 200)
    private String entityName;

    /** Détails complémentaires (quantités, totaux, ...). */
    @Column(length = 500)
    private String details;

    /** Adresse IP du client (si disponible). */
    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
