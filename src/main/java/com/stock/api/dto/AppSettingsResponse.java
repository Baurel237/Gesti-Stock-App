package com.stock.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Paramètres globaux de l'application (lecture / mise à jour).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppSettingsResponse {

    /** Devise d'affichage (code ISO 4217 : EUR, USD, XOF, XAF...). */
    private String currency;

    /** Langue par défaut de l'interface (fr, en). */
    private String locale;

    /** Fuseau horaire d'affichage (IANA, ex : Europe/Paris). */
    private String timezone;

    /** Adresses e-mail notifiées pour les alertes de stock bas. */
    private List<String> lowStockEmails;

    /**
     * Requête de mise à jour des paramètres (PUT /api/settings).
     * Réservée au SUPER_ADMIN.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {

        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "^[A-Z]{3}$", message = "La devise doit être un code ISO 4217 (ex : EUR, USD, XOF)")
        private String currency;

        @NotBlank
        @Size(max = 5)
        @Pattern(regexp = "^(fr|en)$", message = "La langue doit être fr ou en")
        private String locale;

        @Size(max = 50)
        private String timezone;

        @Size(max = 500, message = "Maximum 500 caractères (adresses séparées par des virgules)")
        private String lowStockEmails;
    }
}
