package com.stock.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Préférence de devise d'affichage de l'utilisateur connecté.
 * currency = null signifie "suivre la devise globale".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferenceResponse {
    private String currency;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        /** ISO 4217 (EUR, USD, XOF, XAF, GBP, CHF, CAD, MAD, NGN) ou vide/null pour réinitialiser. */
        private String currency;
    }
}