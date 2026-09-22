package com.stock.api.dto;

import com.stock.api.entity.Sale.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Corps de la demande de modification d'une vente
 * (POST /api/sales/{id}/edit-request).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleEditRequestData {
    /** Motif de la modification demandée (facultatif). */
    @Size(max = 500, message = "Le motif ne peut dépasser 500 caractères")
    private String reason;

    @Valid
    private List<SaleItemRequest> items;

    private String notes;

    /** Mode de paiement ; utilisé tel quel comme nouvelle valeur. */
    private PaymentMethod paymentMethod;

    /** Acheteur proposé (ventes de montants élevés). */
    private String buyerName;

    /** Téléphone de l'acheteur proposé (facultatif). */
    private String buyerPhone;
}