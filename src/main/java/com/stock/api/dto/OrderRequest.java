package com.stock.api.dto;

import com.stock.api.entity.Order.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderRequest {

    /**
     * Type de commande (V10). Absent → STANDARD (comportement historique).
     */
    private OrderType type;

    /**
     * Fournisseur de la commande — obligatoire pour une commande PURCHASE.
     */
    private Long supplierId;

    /**
     * Nom du client — obligatoire pour une commande CLIENT.
     */
    @Size(max = 100, message = "Le nom du client ne doit pas dépasser 100 caractères")
    private String clientName;

    /**
     * Date de livraison souhaitée — obligatoire pour une commande CLIENT.
     */
    private LocalDate deliveryDate;

    @NotEmpty(message = "La commande doit contenir au moins une ligne")
    @Size(max = 50, message = "La commande ne peut pas contenir plus de 50 lignes")
    @Valid
    private List<OrderLineRequest> lines;

    @Size(max = 500, message = "Les notes ne doivent pas dépasser 500 caractères")
    private String notes;
}
