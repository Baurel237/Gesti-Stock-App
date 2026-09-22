package com.stock.api.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Paramètres globaux de l'application (clé → valeur).
 * Une seule ligne (id = 1) : la configuration est unique.
 *
 * Clés gérées :
 *  - currency       : devise d'affichage (EUR, USD, XOF, XAF, GBP, CHF, CAD, MAD, NGN)
 *  - locale         : langue par défaut de l'interface (fr, en)
 *  - timezone       : fuseau horaire d'affichage (ex : Africa/Abidjan)
 *  - lowStockEmails : adresses notifiées en cas d'alerte de stock (séparées par des virgules)
 */
@Entity
@Table(name = "app_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettings {

    /** Identifiant du singleton de configuration (toujours 1). */
    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Devise d'affichage (code ISO 4217). */
    @NotBlank
    @Column(nullable = false, length = 10)
    @Builder.Default
    private String currency = "EUR";

    /** Langue par défaut de l'interface (fr, en). */
    @NotBlank
    @Column(nullable = false, length = 5)
    @Builder.Default
    private String locale = "fr";

    /** Fuseau horaire d'affichage (IANA, ex : Europe/Paris). */
    @Column(length = 50)
    private String timezone;

    /** Adresses e-mail à notifier pour les alertes de stock bas (séparées par des virgules). */
    @Column(length = 500)
    private String lowStockEmails;

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
