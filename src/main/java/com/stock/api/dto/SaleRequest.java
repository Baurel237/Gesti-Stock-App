package com.stock.api.dto;

import com.stock.api.entity.Sale.PaymentMethod;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleRequest {
    @Valid
    private List<SaleItemRequest> items;
    private String notes;

    /** Mode de paiement ; CASH par défaut si absent. */
    private PaymentMethod paymentMethod;

    /** Acheteur optionnel (ventes de montants élevés). */
    private String buyerName;

    /** Téléphone de l'acheteur (facultatif). */
    private String buyerPhone;
}
