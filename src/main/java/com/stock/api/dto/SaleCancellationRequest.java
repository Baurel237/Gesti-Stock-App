package com.stock.api.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Corps facultatif de la demande d'annulation d'une vente
 * (POST /api/sales/{id}/cancellation-request).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleCancellationRequest {

    /** Motif de la demande, facultatif. */
    @Size(max = 500, message = "Le motif ne peut dépasser 500 caractères")
    private String reason;
}
